package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.service.AiCallLogService;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import com.credit.agent.infra.persistence.service.ApprovalTraceService;
import com.credit.agent.orchestration.application.dto.TraceDto;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Trace 回放(H2)。
 * 业务含义: 给定 trace_id 还原一次审批的完整过程 —— 步骤按 step_index 升序(可对齐执行顺序),
 * 模型调用按发生顺序排列, 成本按"各条之和"聚合(不做估算, 没有明细就是 null)。
 */
@Service
public class TraceReplayService {

    private final ApprovalTraceService traceService;
    private final ApprovalStepService stepService;
    private final AiCallLogService aiCallLogService;

    public TraceReplayService(ApprovalTraceService traceService, ApprovalStepService stepService,
                              AiCallLogService aiCallLogService) {
        this.traceService = traceService;
        this.stepService = stepService;
        this.aiCallLogService = aiCallLogService;
    }

    /**
     * 按 trace_id 取回放数据。
     *
     * @param traceId 全链路追踪 ID
     * @return 汇总 + 步骤 + 模型调用明细
     * @throws BizException trace_id 为空(400) 或不存在(404)
     */
    public TraceDto.Replay replay(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw BizException.badRequest("TRACE_ID_REQUIRED", "traceId 不能为空");
        }
        ApprovalTrace trace = traceService.getOne(
                Wrappers.lambdaQuery(ApprovalTrace.class).eq(ApprovalTrace::getTraceId, traceId), false);
        if (trace == null) {
            throw BizException.notFound("TRACE_NOT_FOUND", "trace 不存在: " + traceId);
        }
        List<ApprovalStep> steps = stepService.list(Wrappers.lambdaQuery(ApprovalStep.class)
                .eq(ApprovalStep::getTraceId, traceId)
                .orderByAsc(ApprovalStep::getStepIndex));
        List<AiCallLog> calls = aiCallLogService.list(Wrappers.lambdaQuery(AiCallLog.class)
                .eq(AiCallLog::getTraceId, traceId)
                .orderByAsc(AiCallLog::getId));

        return new TraceDto.Replay(trace.getTraceId(), trace.getApplicationId(), trace.getStatus(),
                trace.getConclusion(), trace.getConfidence(), sumTokens(calls), sumCost(calls),
                trace.getTotalLatencyMs(), trace.getFailureReason(), trace.getRequiresHumanReview(),
                steps.stream().map(TraceReplayService::toStep).toList(),
                calls.stream().map(TraceReplayService::toCall).toList());
    }

    /** token 汇总: 明细条数 = 实际 LLM 调用次数, 累加不得有遗漏。 */
    static long sumTokens(List<AiCallLog> calls) {
        return calls.stream()
                .mapToLong(c -> nz(c.getPromptTokens()) + nz(c.getCompletionTokens()))
                .sum();
    }

    /** 成本汇总: 全为 null(单价未配置)时返回 null, 而不是 0 —— 0 会被误读成"免费"。 */
    static BigDecimal sumCost(List<AiCallLog> calls) {
        List<BigDecimal> costs = calls.stream().map(AiCallLog::getCost).filter(Objects::nonNull).toList();
        if (costs.isEmpty()) {
            return null;
        }
        return costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static TraceDto.StepItem toStep(ApprovalStep step) {
        return new TraceDto.StepItem(step.getStepIndex(), step.getAgentRole(), step.getToolName(),
                step.getInputSummary(), step.getOutputSummary(), step.getLatencyMs(),
                step.getFailureReason(), step.getCreatedAt());
    }

    private static TraceDto.AiCallItem toCall(AiCallLog call) {
        return new TraceDto.AiCallItem(call.getModel(), call.getPromptTokens(), call.getCompletionTokens(),
                call.getCost(), call.getLatencyMs(), call.getCreatedAt());
    }
}
