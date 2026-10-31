package com.credit.agent.orchestration.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.agent.domain.DomainAgent;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.application.exception.BizException;
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
 * 报告生成与锁定(G3)。
 * 业务含义: 一次生成 = 装配域上下文 → 各域分析 → 汇聚 → 组六节 → 落库并**立即锁定**;
 * 重跑不覆盖旧报告, 而是生成 version+1 的新版本, 旧版本只读保留(审计需要)。
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

    public ReportGenerationService(DomainBriefAssembler briefAssembler, DomainAgent domainAgent,
                                   SupervisorAgent supervisor, ReportContentBuilder contentBuilder,
                                   ApprovalReportService reportService,
                                   LoanApplicationService applicationService,
                                   AgentRuntime agentRuntime) {
        this.briefAssembler = briefAssembler;
        this.domainAgent = domainAgent;
        this.supervisor = supervisor;
        this.contentBuilder = contentBuilder;
        this.reportService = reportService;
        this.applicationService = applicationService;
        this.agentRuntime = agentRuntime;
    }

    /**
     * 生成并锁定一份报告。
     * 业务含义: 生成成功即 REPORT_READY, 且 locked=true —— 之后任何内容修改都必须 409,
     * 想改内容只能重新分析产出新版本。
     *
     * @param applicationId 申请单 ID
     * @param traceId       本次分析的全链路 trace
     * @param rules         规则引擎结果(由 domain 计算, 本类只搬运)
     * @param pendingItems  待补材料清单, 可为空
     * @return 新生成报告的只读视图
     * @throws BizException 申请单不存在(404) / 已终态(409)
     */
    public ReportDto.View generate(Long applicationId, String traceId, RuleEngineResult rules,
                                   List<String> pendingItems) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空: 报告必须能按 trace 回放");
        }
        LoanApplication application = requireApplication(applicationId);
        ensureNotTerminal(application);

        List<DomainBrief> briefs = briefAssembler.assemble(applicationId);
        DomainBatchStepExecutor executor = new DomainBatchStepExecutor(domainAgent, traceId, briefs);
        AgentRunResult run = briefs.isEmpty() ? null : agentRuntime.run(executor);
        List<DomainAnswer> answers = run == null ? List.of() : executor.answers();
        FailureReason failureReason = run == null ? null : run.failureReason();
        if (run != null && run.isCompleted()) {
            failureReason = null;
        }

        SupervisionResult supervision = supervisor.aggregate(answers);
        String conclusionBeforeDegrade = supervision.conclusion();
        if (failureReason != null) {
            supervision = degrade(supervision, failureReason);
        }
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
        log.info("报告已生成并锁定: applicationId={}, version={}, conclusion={}, traceId={}, degraded={}",
                applicationId, entity.getVersion(), entity.getConclusion(), traceId, failureReason);
        return toView(entity);
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
     * 降级处理: 分析被掐断时不允许给出"建议通过"。
     * 业务含义: 只分析了 1 个域就下 PASS_SUGGEST 等于用残缺证据背书, 一律降为 NEED_MORE_INFO;
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
