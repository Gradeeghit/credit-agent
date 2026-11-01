package com.credit.agent.tool.registry;

import com.credit.agent.infra.observability.ToolCallRecord;
import com.credit.agent.infra.observability.ToolCallRecorder;
import com.credit.agent.infra.observability.TraceMdc;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.function.Supplier;

/**
 * 工具调用留痕装饰器(H1 的 Tool 段)。
 * 业务含义: 工具是模型在 ChatClient 内部循环里自己调的, 业务代码看不见,
 * 唯一能拦住它的地方就是 ToolCallback 本身 —— 在这里量耗时、抓结果状态, 再交给 recorder 落库。
 * 容错: 留痕失败只影响可观测性, 绝不能影响工具返回给模型的内容。
 */
final class TracedToolCallback implements ToolCallback {

    /** 摘要上限: 落库的是可读片段, 防止把整份检索结果写进库 */
    private static final int SUMMARY_LIMIT = 500;

    private final ToolCallback delegate;
    private final ToolCallRecorder recorder;

    TracedToolCallback(ToolCallback delegate, ToolCallRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    /** 批量包装; recorder 为 null 时原样返回, 便于无追踪场景(单测/离线)。 */
    static ToolCallback[] wrap(ToolCallback[] callbacks, ToolCallRecorder recorder) {
        if (callbacks == null || recorder == null) {
            return callbacks;
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new TracedToolCallback(callbacks[i], recorder);
        }
        return wrapped;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return traced(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return traced(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    /** 计时 + 留痕; 异常照常抛出(交给模型侧的工具执行器处理), 但失败也要留一条。 */
    private String traced(String toolInput, Supplier<String> invocation) {
        long startNanos = System.nanoTime();
        String output = null;
        try {
            output = invocation.get();
            return output;
        } catch (RuntimeException e) {
            output = "ERROR: " + e.getMessage();
            throw e;
        } finally {
            record(toolInput, output, (System.nanoTime() - startNanos) / 1_000_000L);
        }
    }

    /** 只有处在某个 trace 的具体步骤内才落库, 否则(单测/离线)直接跳过。 */
    private void record(String input, String output, long latencyMs) {
        String traceId = TraceMdc.traceId();
        if (traceId == null) {
            return;
        }
        recorder.record(new ToolCallRecord(traceId, TraceMdc.stepIndex(), getToolDefinition().name(),
                truncate(input), truncate(output), latencyMs, failureReasonOf(output)));
    }

    /** 从 ToolResponse 的 JSON 里折出 approval_step.failure_reason 的枚举值; 认不出就不猜(null)。 */
    static String failureReasonOf(String json) {
        String code = fieldOf(json, "\"code\":\"");
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "TOOL_TIMEOUT" -> "TOOL_TIMEOUT";
            case "PARAM_INVALID" -> "TOOL_PARAM_INVALID";
            case "NOT_FOUND" -> "TOOL_NOT_FOUND";
            case "RETRIEVAL_FAILED" -> "RETRIEVAL_DEGRADED";
            default -> null;
        };
    }

    private static String fieldOf(String json, String prefix) {
        if (json == null) {
            return null;
        }
        int from = json.indexOf(prefix);
        if (from < 0) {
            return null;
        }
        int start = from + prefix.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "...";
    }
}
