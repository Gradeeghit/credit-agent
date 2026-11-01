package com.credit.agent.orchestration.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trace 回放契约(H2)。
 * 业务含义: 给定 trace_id 能还原「HTTP → Agent → LLM → Tool → DB」全过程:
 * steps 是 Agent/Tool 段(按 step_index 升序), aiCalls 是 LLM 段, totalCost 是成本汇总。
 */
public final class TraceDto {

    private TraceDto() {
    }

    /** 一次审批的完整回放数据。 */
    public record Replay(
            String traceId,
            Long applicationId,
            String status,
            String conclusion,
            BigDecimal confidence,
            Long totalTokens,
            BigDecimal totalCost,
            Long totalLatencyMs,
            String failureReason,
            Boolean requiresHumanReview,
            List<StepItem> steps,
            List<AiCallItem> aiCalls) {
    }

    /** 单步明细(Agent / Tool 段)。 */
    public record StepItem(
            Integer stepIndex,
            String agentRole,
            String toolName,
            String inputSummary,
            String outputSummary,
            Long latencyMs,
            String failureReason,
            LocalDateTime createdAt) {
    }

    /** 单次模型调用(LLM 段)。 */
    public record AiCallItem(
            String model,
            Long promptTokens,
            Long completionTokens,
            BigDecimal cost,
            Long latencyMs,
            LocalDateTime createdAt) {
    }
}
