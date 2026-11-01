package com.credit.agent.orchestration.application.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 全链路 trace 上下文(H1)。
 * 业务含义: 一次审批从 HTTP 进来就生成一个 trace_id, 之后 Agent / LLM / Tool / DB 所有日志与落库都带它,
 * "给定 trace_id 能还原整条链路"才成立。
 * 技术说明: 用 SLF4J MDC 承载, 由 api 层过滤器写入与清理, 业务代码只读不写。
 * 放在 orchestration 而非 infra: api 不允许直接依赖 infra, 而过滤器与编排层都要用它。
 */
public final class TraceContext {

    /** 请求/响应头名称: 允许调用方自带 trace_id(便于串前端链路) */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** MDC key, 与日志 pattern 里的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    /** 生成本系统格式的 trace_id。 */
    public static String newTraceId() {
        return "trace-" + UUID.randomUUID();
    }

    /** 绑定 trace_id 到当前线程; 过滤器入口调用。 */
    public static void bind(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            MDC.remove(MDC_KEY);
            return;
        }
        MDC.put(MDC_KEY, traceId);
    }

    /** 当前线程的 trace_id, 无则返回 null。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** 当前线程的 trace_id; 没有时返回兜底值(不写 MDC, 避免污染后台任务上下文)。 */
    public static String currentOr(String fallback) {
        String current = MDC.get(MDC_KEY);
        return (current == null || current.isBlank()) ? fallback : current;
    }

    /** 清理, 必须在过滤器 finally 中调用, 防止线程池复用导致串号。 */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
