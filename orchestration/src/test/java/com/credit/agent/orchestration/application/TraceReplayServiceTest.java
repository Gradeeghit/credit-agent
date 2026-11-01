package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.service.AiCallLogService;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import com.credit.agent.infra.persistence.service.ApprovalTraceService;
import com.credit.agent.orchestration.application.dto.TraceDto;
import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** H2 回放单测: 步骤顺序、token 汇总、成本"各条之和"。 */
class TraceReplayServiceTest {

    private final ApprovalTraceService traceService = mock(ApprovalTraceService.class);
    private final ApprovalStepService stepService = mock(ApprovalStepService.class);
    private final AiCallLogService aiCallLogService = mock(AiCallLogService.class);
    private final TraceReplayService service =
            new TraceReplayService(traceService, stepService, aiCallLogService);

    @Test
    @DisplayName("回放: 步骤按 step_index 返回, token 与成本按明细求和")
    void replaysTraceWithAggregates() {
        when(traceService.getOne(any(Wrapper.class), eq(false))).thenReturn(trace());
        when(stepService.list(any(Wrapper.class))).thenReturn(List.of(step(1, "MATERIAL"), step(2, "STATEMENT")));
        when(aiCallLogService.list(any(Wrapper.class))).thenReturn(List.of(
                call("deepseek-chat", 100, 50, "0.003000"),
                call("deepseek-chat", 200, 80, "0.005000")));

        TraceDto.Replay replay = service.replay("trace-1");

        assertEquals("trace-1", replay.traceId());
        assertEquals(2, replay.steps().size());
        assertEquals("MATERIAL", replay.steps().get(0).agentRole());
        assertEquals("STATEMENT", replay.steps().get(1).agentRole());
        assertEquals(2, replay.aiCalls().size());
        assertEquals(430L, replay.totalTokens());
        assertEquals(new BigDecimal("0.008000"), replay.totalCost());
    }

    @Test
    @DisplayName("单价未配置(cost 全为 null)时总成本返回 null, 而不是 0")
    void nullCostStaysNull() {
        when(traceService.getOne(any(Wrapper.class), eq(false))).thenReturn(trace());
        when(stepService.list(any(Wrapper.class))).thenReturn(List.of());
        when(aiCallLogService.list(any(Wrapper.class))).thenReturn(List.of(call("m", 10, 10, null)));

        TraceDto.Replay replay = service.replay("trace-1");

        assertNull(replay.totalCost());
        assertEquals(20L, replay.totalTokens());
        assertEquals(0, replay.steps().size());
    }

    @Test
    @DisplayName("traceId 为空 400; 不存在 404")
    void invalidInputs() {
        assertEquals(400, assertThrows(BizException.class, () -> service.replay(" ")).getStatus());

        when(traceService.getOne(any(Wrapper.class), eq(false))).thenReturn(null);
        assertEquals(404, assertThrows(BizException.class, () -> service.replay("trace-x")).getStatus());
    }

    private static ApprovalTrace trace() {
        ApprovalTrace trace = new ApprovalTrace();
        trace.setTraceId("trace-1");
        trace.setApplicationId(9L);
        trace.setStatus(ApprovalTrace.STATUS_COMPLETED);
        trace.setConclusion("PASS_SUGGEST");
        trace.setConfidence(new BigDecimal("0.9000"));
        trace.setRequiresHumanReview(true);
        return trace;
    }

    private static ApprovalStep step(int index, String role) {
        ApprovalStep step = new ApprovalStep();
        step.setTraceId("trace-1");
        step.setStepIndex(index);
        step.setAgentRole(role);
        step.setLatencyMs(100L);
        return step;
    }

    private static AiCallLog call(String model, long prompt, long completion, String cost) {
        AiCallLog log = new AiCallLog();
        log.setTraceId("trace-1");
        log.setModel(model);
        log.setPromptTokens(prompt);
        log.setCompletionTokens(completion);
        log.setCost(cost == null ? null : new BigDecimal(cost));
        log.setLatencyMs(50L);
        return log;
    }
}
