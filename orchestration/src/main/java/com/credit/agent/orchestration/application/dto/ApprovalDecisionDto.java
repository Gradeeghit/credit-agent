package com.credit.agent.orchestration.application.dto;

/**
 * 签批的请求/响应契约(G4)。
 * 业务含义: 签批必须携带报告版本号 —— 主管看到的报告与库里的报告必须是同一版,
 * 否则可能出现"看着 v1 的结论签了 v2 的单"。
 */
public final class ApprovalDecisionDto {

    private ApprovalDecisionDto() {
    }

    /**
     * 签批请求。
     *
     * @param action  APPROVE / REJECT / NEED_MORE_INFO
     * @param version 主管看到的报告版本, 必须等于当前最新版本
     * @param comment 签批意见; 驳回与要求补材料必填, 需人工复核时通过也必填(≥10 字)
     */
    public record Request(String action, Integer version, String comment) {
    }

    /**
     * 签批结果。
     *
     * @param applicationId       申请单 ID
     * @param reportId            被签批的报告 ID
     * @param reportVersion       被签批的报告版本
     * @param action              实际执行的动作
     * @param status              签批后申请单状态
     * @param requiresHumanReview 该报告是否需要人工复核(前端据此解释"为什么必须写意见")
     * @param idempotent          是否为重复请求命中首次结果(此时未产生任何新的留痕)
     */
    public record Result(Long applicationId, Long reportId, Integer reportVersion, String action,
                         String status, boolean requiresHumanReview, boolean idempotent) {
    }
}
