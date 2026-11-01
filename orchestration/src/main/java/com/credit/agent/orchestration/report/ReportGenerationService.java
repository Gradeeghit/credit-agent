package com.credit.agent.orchestration.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.credit.agent.agent.domain.CitationRef;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.agent.domain.DomainAgent;
import com.credit.agent.agent.llm.LlmCallException;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.application.observability.TraceRecorder;
import com.credit.agent.orchestration.runtime.AgentRunResult;
import com.credit.agent.orchestration.runtime.AgentRuntime;
import com.credit.agent.orchestration.runtime.DomainBriefAssembler;
import com.credit.agent.orchestration.runtime.FailureReason;
import com.credit.agent.orchestration.runtime.SupervisionResult;
import com.credit.agent.orchestration.runtime.SupervisorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报告生成与锁定(G3 + H1 + H9)。
 * 业务含义: 一次生成 = 落 trace 起点 → 装配域上下文 → 经 AgentRuntime 逐域分析(步数/token 双终止)
 * → 汇聚 → 组六节 → 落库并**立即锁定** → 回填 trace 终态。
 * 模型不可用时**不抛异常给调用方**, 而是产出降级报告(标 AI_DEGRADED), 让流程能继续走人工签批。
 * 本类不计算任何金额: 规则引擎结果由调用方传入(来自 domain)。
 */
@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);

    private final DomainBriefAssembler briefAssembler;
    private final DomainAgent domainAgent;
    private final SupervisorAgent supervisor;
    private final ReportContentBuilder contentBuilder;
    private final ApprovalReportService reportService;
    private final LoanApplicationService applicationService;
    private final AgentRuntime agentRuntime;
    private final TraceRecorder traceRecorder;

    public ReportGenerationService(DomainBriefAssembler briefAssembler, DomainAgent domainAgent,
                                   SupervisorAgent supervisor, ReportContentBuilder contentBuilder,
                                   ApprovalReportService reportService,
                                   LoanApplicationService applicationService,
                                   AgentRuntime agentRuntime, TraceRecorder traceRecorder) {
        this.briefAssembler = briefAssembler;
        this.domainAgent = domainAgent;
        this.supervisor = supervisor;
        this.contentBuilder = contentBuilder;
        this.reportService = reportService;
        this.applicationService = applicationService;
        this.agentRuntime = agentRuntime;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 生成并锁定一份报告(只要报告视图)。
     *
     * @throws BizException 申请单不存在(404) / 已终态(409)
     */
    public ReportDto.View generate(Long applicationId, String traceId, RuleEngineResult rules,
                                   List<String> pendingItems) {
        return generateWithTrace(applicationId, traceId, rules, pendingItems).report();
    }

    /**
     * 生成并锁定报告, 同时返回是否降级(H9 的 degraded 标记来源)。
     * 业务含义: 生成成功即 REPORT_READY 且 locked=true; 模型不可用或预算耗尽时降级出报告, 不中断流程。
     *
     * @param applicationId 申请单 ID
     * @param traceId       全链路 trace, 不能为空(必须能回放)
     * @param rules         规则引擎结果(由 domain 计算)
     * @param pendingItems  待补材料清单, 可为空
     * @return 报告视图 + 降级原因
     */
    public GeneratedReport generateWithTrace(Long applicationId, String traceId, RuleEngineResult rules,
                                             List<String> pendingItems) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空: 报告必须能按 trace 回放");
        }
        LoanApplication application = requireApplication(applicationId);
        ensureNotTerminal(application);
        traceRecorder.start(traceId, applicationId);
        long startNanos = System.nanoTime();

        List<DomainBrief> briefs = briefAssembler.assemble(applicationId);
        DomainBatchStepExecutor executor = new DomainBatchStepExecutor(domainAgent, traceId, briefs,
                event -> traceRecorder.step(traceId, event.stepIndex(), event.role().name(), null,
                        "domain=" + event.role(), event.observation(), event.latencyMs(), null));

        FailureReason failureReason = null;
        List<DomainAnswer> answers;
        try {
            AgentRunResult run = briefs.isEmpty() ? null : agentRuntime.run(executor);
            answers = executor.answers();
            if (run != null && !run.isCompleted()) {
                failureReason = run.failureReason();
            }
        } catch (LlmCallException e) {
            log.warn("模型不可用, 转降级报告: traceId={}, err={}", traceId, e.getMessage());
            failureReason = FailureReason.LLM_TIMEOUT;
            answers = executor.answers();
        }

        SupervisionResult supervision = supervisor.aggregate(answers);
        String conclusionBeforeDegrade = supervision.conclusion();
        if (failureReason != null) {
            supervision = degrade(supervision, failureReason);
        }
        supervision = withEvidenceCitations(supervision, briefs);
        ReportContent content = contentBuilder.build(supervision, rules, pendingItems, failureReason,
                conclusionBeforeDegrade);

        ApprovalReport entity = new ApprovalReport();
        entity.setApplicationId(applicationId);
        entity.setTraceId(traceId);
        entity.setVersion(nextVersion(applicationId));
        entity.setConclusion(supervision.conclusion());
        entity.setContentJson(ReportContentJson.write(content));
        entity.setRequiresHumanReview(supervision.requiresHumanReview() || failureReason != null);
        entity.setLocked(true);
        reportService.save(entity);

        markReportReady(application, entity.getId());
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        traceRecorder.finish(traceId,
                failureReason == null ? ApprovalTrace.STATUS_COMPLETED : ApprovalTrace.STATUS_FAILED,
                entity.getConclusion(), content.confidence(), latencyMs, supervision.tokensUsed(), null,
                Boolean.TRUE.equals(entity.getRequiresHumanReview()),
                failureReason == null ? null : failureReason.name());
        log.info("报告已生成并锁定: applicationId={}, version={}, conclusion={}, traceId={}, degraded={}",
                applicationId, entity.getVersion(), entity.getConclusion(), traceId, failureReason);
        return new GeneratedReport(toView(entity), failureReason);
    }

    /** 下一个版本号: 取当前最大版本 + 1, 首次为 1(与 UNIQUE(application_id, version) 配合)。 */
    private int nextVersion(Long applicationId) {
        ApprovalReport latest = reportService.getOne(Wrappers.lambdaQuery(ApprovalReport.class)
                .eq(ApprovalReport::getApplicationId, applicationId)
                .orderByDesc(ApprovalReport::getVersion)
                .last("LIMIT 1"), false);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    /** 报告就绪后把申请单推进到 REPORT_READY; 终态不允许回退(前置已拦)。 */
    private void markReportReady(LoanApplication application, Long reportId) {
        LoanApplication patch = new LoanApplication();
        patch.setId(application.getId());
        patch.setStatus(LoanApplication.STATUS_REPORT_READY);
        applicationService.updateById(patch);
        log.debug("申请单状态更新: applicationId={}, status=REPORT_READY, reportId={}",
                application.getId(), reportId);
    }

    private LoanApplication requireApplication(Long applicationId) {
        LoanApplication application = applicationService.getById(applicationId);
        if (application == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + applicationId);
        }
        return application;
    }

    /** 终态(APPROVED/REJECTED)不可再生成报告, 避免已终态的单子被 AI 改写结论。 */
    private static void ensureNotTerminal(LoanApplication application) {
        String status = application.getStatus();
        if (LoanApplication.STATUS_APPROVED.equals(status) || LoanApplication.STATUS_REJECTED.equals(status)) {
            throw BizException.conflict("APPLICATION_TERMINAL",
                    "申请单已处于终态 " + status + ", 不允许重新生成报告");
        }
    }

    /**
     * 引用兜底: 模型完全不可用时没有任何域结论, 引用为空会导致报告无法成立(G1 要求引用非空)。
     * 业务含义: 此时"依据引用"退化为"本次分析读取了哪些材料证据", 仍然可回链 chunk —— 宁可引用少, 不能没有。
     */
    private static SupervisionResult withEvidenceCitations(SupervisionResult supervision,
                                                           List<DomainBrief> briefs) {
        if (!supervision.citations().isEmpty() || briefs == null || briefs.isEmpty()) {
            return supervision;
        }
        List<CitationRef> citations = briefs.stream()
                .flatMap(brief -> brief.evidence().stream())
                .map(e -> new CitationRef(e.chunkId(), e.docName(), e.pageStart(), e.pageEnd()))
                .distinct()
                .toList();
        return new SupervisionResult(supervision.conclusion(), supervision.conflicts(), citations,
                supervision.requiresHumanReview(), supervision.tokensUsed());
    }

    /**
     * 降级处理: 分析被掐断时不允许给出"建议通过"。
     * 业务含义: 只分析了部分域就下 PASS_SUGGEST 等于用残缺证据背书, 一律降为 NEED_MORE_INFO;
     * 已识别的 CONFLICT 保留 —— 冲突本身就是必须人看的结论。
     */
    private static SupervisionResult degrade(SupervisionResult supervision, FailureReason reason) {
        if (SupervisionResult.CONFLICT.equals(supervision.conclusion())) {
            return supervision;
        }
        log.warn("AI 分析被终止({}), 报告降级为 NEED_MORE_INFO", reason);
        return new SupervisionResult(SupervisionResult.NEED_MORE_INFO, supervision.conflicts(),
                supervision.citations(), true, supervision.tokensUsed());
    }

    private static ReportDto.View toView(ApprovalReport report) {
        return new ReportDto.View(report.getId(), report.getApplicationId(), report.getTraceId(),
                report.getVersion(), report.getConclusion(), report.getContentJson(),
                report.getRequiresHumanReview(), report.getLocked(), report.getCreatedAt());
    }
}
