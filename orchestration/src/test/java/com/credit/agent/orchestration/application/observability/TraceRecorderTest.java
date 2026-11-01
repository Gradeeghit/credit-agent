package com.credit.agent.orchestration.application.observability;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import com.credit.agent.infra.persistence.service.ApprovalTraceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** H1 落库单测: 起点/每步/终态三段, 以及写失败不阻断。 */
class TraceRecorderTest {

    private final ApprovalTraceService traceService = mock(ApprovalTraceService.class);
    private final ApprovalStepService stepService = mock(ApprovalStepService.class);
    private final TraceRecorder recorder = new TraceRecorder(traceService, stepService);

    @Test
    @DisplayName("start 落 RUNNING; finish 回填状态与汇总")
    void startAndFinish() {
        recorder.start("trace-1", 9L);

        ArgumentCaptor<ApprovalTrace> captor = ArgumentCaptor.forClass(ApprovalTrace.class);
        verify(traceService).save(captor.capture());
        assertEquals("trace-1", captor.getValue().getTraceId());
        assertEquals(9L, captor.getValue().getApplicationId());
        assertEquals(ApprovalTrace.STATUS_RUNNING, captor.getValue().getStatus());

        recorder.finish("trace-1", ApprovalTrace.STATUS_COMPLETED, "PASS_SUGGEST",
                new BigDecimal("0.9000"), 1234L, 300L, new BigDecimal("0.006000"), true, null);

        verify(traceService).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("step 落库并对超长摘要截断(不把整段 prompt 写进库)")
    void stepTruncatesSummary() {
        String longText = "x".repeat(900);

        recorder.step("trace-1", 1, "MATERIAL", null, longText, longText, 88L, null);

        ArgumentCaptor<ApprovalStep> captor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(stepService).save(captor.capture());
        ApprovalStep step = captor.getValue();
        assertEquals(1, step.getStepIndex());
        assertEquals("MATERIAL", step.getAgentRole());
        assertTrue(step.getInputSummary().length() < longText.length());
        assertTrue(step.getInputSummary().endsWith("..."));
    }

    @Test
    @DisplayName("写库失败只告警, 不把异常抛给审批主流程")
    void writeFailureIsSwallowed() {
        doThrow(new IllegalStateException("db down")).when(traceService).save(any());

        recorder.start("trace-1", 9L);

        verify(traceService).save(any());
    }

    @Test
    @DisplayName("traceId 为空时直接跳过, 不产生任何写入")
    void blankTraceIdSkipped() {
        recorder.start(" ", 9L);
        recorder.step("", 1, "MATERIAL", null, "in", "out", 1L, null);
        recorder.finish("", ApprovalTrace.STATUS_COMPLETED, "PASS_SUGGEST", null, 1L, 1L, null, true, null);

        verify(traceService, never()).save(any());
        verify(traceService, never()).update(any(Wrapper.class));
        verify(stepService, never()).save(any());
    }

    @Test
    @DisplayName("失败原因与状态原样落库")
    void recordsFailureReason() {
        recorder.finish("trace-2", ApprovalTrace.STATUS_FAILED, "NEED_MORE_INFO",
                new BigDecimal("0.5000"), 100L, 10L, null, true, "LLM_TIMEOUT");

        verify(traceService).update(any(Wrapper.class));
        clearInvocations(traceService);
    }
}
