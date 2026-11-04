package com.credit.agent.orchestration.application.observability;

import com.credit.agent.infra.persistence.entity.AuditLog;
import com.credit.agent.infra.persistence.service.AuditLogService;
import com.credit.agent.orchestration.application.ApprovalDecisionRules;
import com.credit.agent.orchestration.application.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审计留痕(H6)。
 * 业务含义: 每一条"人做的决定"都要能回答 who / when / what / before / after;
 * 本实现只追加记录, 不改不删, 因此审计可以回答"当时到底看到的是什么状态"。
 * 容错: 写失败只告警不阻断 —— 审计缺失可以补, 签批中断不可接受(与 TraceRecorder 同一取舍)。
 */
@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    /** 意见原文入库上限: 审计要留原话, 但不能被超长文本撑爆 */
    private static final int DETAIL_LIMIT = 1000;

    private final AuditLogService auditLogService;

    public AuditRecorder(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 记录一次签批动作。
     *
     * @param operator     操作人(必须是 APPROVER, 权限校验在调用方完成)
     * @param traceId      本次签批请求的 trace, 用于与 AI 分析链路区分/串联
     * @param applicationId 被签批的申请单
     * @param reportVersion 被签批的报告版本
     * @param requiresHumanReview 该报告是否标记为需人工复核
     * @param decision     签批动作
     * @param comment      签批意见原文, 可为 null
     */
    public void recordDecision(CurrentUser operator, String traceId, Long applicationId, Integer reportVersion,
                               boolean requiresHumanReview, ApprovalDecisionRules.Decision decision,
                               String comment) {
        AuditLog entry = new AuditLog();
        entry.setTraceId(traceId);
        entry.setOperatorId(operator.id());
        entry.setOperatorRole(operator.role().name());
        entry.setAction(decision.name());
        entry.setTargetType(AuditLog.TARGET_APPLICATION);
        entry.setTargetId(applicationId);
        entry.setBeforeJson("status=REPORT_READY;version=" + reportVersion
                + ";requiresHumanReview=" + requiresHumanReview);
        entry.setAfterJson("status=" + decision.targetStatus() + ";version=" + reportVersion);
        entry.setDetail(truncate(comment));
        entry.setOutcome(AuditLog.OUTCOME_SUCCESS);
        try {
            auditLogService.save(entry);
        } catch (RuntimeException e) {
            log.warn("audit_log 写入失败, 已忽略: applicationId={}, action={}, err={}",
                    applicationId, decision.name(), e.getMessage());
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= DETAIL_LIMIT ? text : text.substring(0, DETAIL_LIMIT) + "...";
    }
}
