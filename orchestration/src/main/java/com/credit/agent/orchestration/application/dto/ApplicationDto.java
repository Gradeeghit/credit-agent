package com.credit.agent.orchestration.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 申请单对外契约(A1/A7)。
 * 业务含义: 客户提交的借款申请; 金额、期限由 D2/D3/D6 规则引擎校验, 本契约只做字段搬运。
 */
public final class ApplicationDto {

    private ApplicationDto() {
    }

    /** 提交申请入参; clientRequestId 是客户维度幂等键, 重复提交返回首次结果。 */
    public record SubmitRequest(
            Long customerId,
            Long productId,
            String clientRequestId,
            BigDecimal amount,
            Integer termMonths,
            String purpose,
            String purposeDesc,
            BigDecimal income,
            String incomeType) {
    }

    /** 修改申请入参; 仅 SUBMITTED 状态可改, 状态迁移不在此接口。 */
    public record UpdateRequest(
            BigDecimal amount,
            Integer termMonths,
            String purpose,
            String purposeDesc,
            BigDecimal income,
            String incomeType) {
    }

    /** 申请单视图。 */
    public record View(
            Long id,
            String appNo,
            Long customerId,
            Long productId,
            String clientRequestId,
            BigDecimal amount,
            Integer termMonths,
            String purpose,
            String purposeDesc,
            BigDecimal income,
            String incomeType,
            String status,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
