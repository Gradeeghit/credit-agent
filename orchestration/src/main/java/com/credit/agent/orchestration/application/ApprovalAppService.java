package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.ApprovalDecisionDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.application.observability.AuditRecorder;
import com.credit.agent.orchestration.application.observability.TraceContext;
import com.credit.agent.orchestration.application.observability.TraceRecorder;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 主管签批(G4): 通过 / 驳回 / 要求补材料。
 * 业务含义: AI 永远不产生终态, 只有主管签批才能把申请单推进到 APPROVED / REJECTED;
 * 签批必须携带报告版本, 版本不匹配即 409(防止"看着 v1 的结论签了 v2 的单");
 * 同一 (申请单, 版本, 动作) 的重复请求返回首次结果, 且不再产生新的审批步骤与审计记录。
 * <p>
 * 并发: 状态迁移用 "UPDATE ... WHERE id=? AND status=REPORT_READY" 做乐观校验,
 * 影响行数为 0 说明已被别的请求改过, 直接 409, 不会出现两个人同时签批都成功。
 */
@Service
public class ApprovalAppService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalAppService.class);

    private final LoanApplicationService applicationService;
    private final ApprovalReportService reportService;
    private final AuditRecorder auditRecorder;
    private final TraceRecorder traceRecorder;

    public ApprovalAppService(LoanApplicationService applicationService, ApprovalReportService reportService,
                              AuditRecorder auditRecorder, TraceRecorder traceRecorder) {
        this.applicationService = applicationService;
        this.reportService = reportService;
        this.auditRecorder = auditRecorder;
        this.traceRecorder = traceRecorder;
    }

    /**
     * 执行一次签批。
     *
     * @param applicationId 申请单 ID
     * @param req           动作 + 报告版本 + 意见
     * @param operator      操作人, 必须为 APPROVER
     * @param traceId       本次签批请求的 trace, 为空时取请求上下文或新生成
     * @return 签批结果; idempotent=true 表示重复请求命中首次结果
     * @throws BizException 403 非主管 / 400 动作或意见不合法 / 404 单子或报告不存在 /
     *                      409 版本不匹配或当前状态不允许签批
     */
    public ApprovalDecisionDto.Result decide(Long applicationId, ApprovalDecisionDto.Request req,
                                             CurrentUser operator, String traceId) {
        operator.requireAny(UserRole.APPROVER);
        if (req == null) {
            throw BizException.badRequest("DECISION_REQUIRED", "签批请求不能为空");
        }
        ApprovalDecisionRules.Decision decision = ApprovalDecisionRules.Decision.from(req.action());
        if (req.version() == null || req.version() < 1) {
            throw BizException.badRequest("REPORT_VERSION_REQUIRED", "签批必须携带报告版本号");
        }
        LoanApplication application = requireApplication(applicationId);
        ApprovalReport report = requireLatestReport(applicationId);
        if (!req.version().equals(report.getVersion())) {
            throw BizException.conflict("REPORT_VERSION_MISMATCH",
                    "报告版本已变化: 请求 version=" + req.version()
                            + ", 当前最新 version=" + report.getVersion() + ", 请刷新后重新签批");
        }
        boolean review = Boolean.TRUE.equals(report.getRequiresHumanReview());
        String targetStatus = decision.targetStatus();

        // 幂等: 申请单已经是该动作的目标状态, 说明这次是重复提交, 返回首次结果且不新增留痕
        if (targetStatus.equals(application.getStatus())) {
            log.info("签批重复请求命中首次结果: applicationId={}, action={}, version={}",
                    applicationId, decision.name(), report.getVersion());
            return result(application, report, decision, review, true);
        }
        if (!LoanApplication.STATUS_REPORT_READY.equals(application.getStatus())) {
            throw BizException.conflict("APPLICATION_STATUS_INVALID",
                    "申请单当前状态为 " + application.getStatus() + ", 只有 REPORT_READY 才能签批");
        }
        ApprovalDecisionRules.validateComment(decision, review, req.comment());

        boolean moved = applicationService.update(new UpdateWrapper<LoanApplication>()
                .eq("id", applicationId)
                .eq("status", LoanApplication.STATUS_REPORT_READY)
                .set("status", targetStatus)
                .set("updated_at", LocalDateTime.now()));
        if (!moved) {
            throw BizException.conflict("APPLICATION_STATUS_INVALID",
                    "申请单状态已被其他请求变更, 请刷新后重新签批");
        }

        String trace = (traceId == null || traceId.isBlank())
                ? TraceContext.currentOr(TraceContext.newTraceId()) : traceId;
        auditRecorder.recordDecision(operator, trace, applicationId, report.getVersion(), review, decision,
                ApprovalDecisionRules.normalizeComment(req.comment()));
        traceRecorder.step(trace, 1, UserRole.APPROVER.name(), null,
                "action=" + decision.name() + ",version=" + report.getVersion(),
                "status=" + targetStatus, 0L, null);

        application.setStatus(targetStatus);
        log.info("签批完成: applicationId={}, action={}, version={}, operator={}, traceId={}",
                applicationId, decision.name(), report.getVersion(), operator.id(), trace);
        return result(application, report, decision, review, false);
    }

    private LoanApplication requireApplication(Long applicationId) {
        LoanApplication application = applicationService.getById(applicationId);
        if (application == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + applicationId);
        }
        return application;
    }

    /** 取最新版本报告: 签批只对最新版本生效, 旧版本仅供审计查看。 */
    private ApprovalReport requireLatestReport(Long applicationId) {
        ApprovalReport latest = reportService.getOne(new QueryWrapper<ApprovalReport>()
                .eq("application_id", applicationId)
                .orderByDesc("version")
                .last("LIMIT 1"), false);
        if (latest == null) {
            throw BizException.notFound("REPORT_NOT_FOUND", "申请单暂无初审报告: " + applicationId);
        }
        return latest;
    }

    private static ApprovalDecisionDto.Result result(LoanApplication application, ApprovalReport report,
                                                     ApprovalDecisionRules.Decision decision,
                                                     boolean requiresHumanReview, boolean idempotent) {
        return new ApprovalDecisionDto.Result(application.getId(), report.getId(), report.getVersion(),
                decision.name(), application.getStatus(), requiresHumanReview, idempotent);
    }
}
