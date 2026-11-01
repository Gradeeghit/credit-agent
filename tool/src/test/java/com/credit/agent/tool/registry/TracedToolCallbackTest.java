package com.credit.agent.tool.registry;

import com.credit.agent.infra.observability.ToolCallRecord;
import com.credit.agent.infra.observability.ToolCallRecorder;
import com.credit.agent.infra.observability.TraceMdc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 工具留痕: 每次调用都要带上 traceId/步号/工具名/失败原因, 且不影响工具本身的返回。 */
class TracedToolCallbackTest {

    private final AtomicReference<ToolCallRecord> captured = new AtomicReference<>();
    private final ToolCallRecorder recorder = captured::set;

    private static ToolCallback delegate(String toolName, String result) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(toolName);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call("{\"keyword\":\"逾期\"}")).thenReturn(result);
        return callback;
    }

    private ToolCallback traced(ToolCallback delegate) {
        return TracedToolCallback.wrap(new ToolCallback[]{delegate}, recorder)[0];
    }

    @AfterEach
    void clearMdc() {
        TraceMdc.clearStepIndex();
        MDC.remove(TraceMdc.TRACE_ID);
    }

    @Test
    @DisplayName("正常调用: 记录 traceId/步号/工具名, 失败原因为空, 返回值原样透传")
    void recordsSuccessfulCall() {
        MDC.put(TraceMdc.TRACE_ID, "trace-1");
        TraceMdc.bindStepIndex(2);
        ToolCallback callback = traced(delegate("searchPolicy", "{\"status\":\"OK\",\"code\":null,\"data\":[]}"));

        String result = callback.call("{\"keyword\":\"逾期\"}");

        assertEquals("{\"status\":\"OK\",\"code\":null,\"data\":[]}", result);
        ToolCallRecord record = captured.get();
        assertNotNull(record);
        assertEquals("trace-1", record.traceId());
        assertEquals(2, record.stepIndex());
        assertEquals("searchPolicy", record.toolName());
        assertNull(record.failureReason());
    }

    @Test
    @DisplayName("降级调用: TOOL_TIMEOUT 要折成 approval_step 的枚举值")
    void mapsDegradedReason() {
        MDC.put(TraceMdc.TRACE_ID, "trace-2");
        TraceMdc.bindStepIndex(4);
        ToolCallback callback = traced(delegate("searchPolicy",
                "{\"status\":\"DEGRADED\",\"code\":\"TOOL_TIMEOUT\",\"message\":\"超时\",\"data\":[]}"));

        callback.call("{\"keyword\":\"逾期\"}");

        assertEquals("TOOL_TIMEOUT", captured.get().failureReason());
    }

    @Test
    @DisplayName("没有 traceId 时不落库(单测/离线调用), 也不抛错")
    void skipsWithoutTrace() {
        ToolCallback callback = traced(delegate("queryProduct", "{\"status\":\"OK\",\"code\":null,\"data\":null}"));

        callback.call("{\"keyword\":\"逾期\"}");

        assertNull(captured.get());
    }

    @Test
    @DisplayName("工具抛异常: 异常照常抛出, 但留痕仍要写一条")
    void recordsFailureAndRethrows() {
        MDC.put(TraceMdc.TRACE_ID, "trace-3");
        TraceMdc.bindStepIndex(1);
        ToolCallback delegate = delegate("searchPolicy", null);
        when(delegate.call("{\"keyword\":\"逾期\"}")).thenThrow(new IllegalStateException("vector db down"));

        assertThrows(IllegalStateException.class, () -> traced(delegate).call("{\"keyword\":\"逾期\"}"));

        assertNotNull(captured.get());
        assertEquals("searchPolicy", captured.get().toolName());
    }
}
