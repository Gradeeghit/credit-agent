package com.credit.agent.infra.observability;

import org.slf4j.MDC;

/**
 * 链路上下文在 MDC 里的键与读取入口。
 * 业务含义: 工具调用发生在 ChatModel 内部循环里, 拿不到编排层的参数,
 * 只能靠线程上下文知道"自己属于哪个 trace 的第几步"; 键名统一放 infra,
 * 避免 api(过滤器) / orchestration(编排) / tool(工具) 三处各写一份字符串而漂移。
 * 放在 infra 而非 orchestration: tool 不能反向依赖 orchestration。
 */
public final class TraceMdc {

    /** 全链路追踪 ID, 与日志 pattern 的 %X{traceId} 一致 */
    public static final String TRACE_ID = "traceId";

    /** 当前正在执行的步骤序号, 与 approval_step.step_index 对齐 */
    public static final String STEP_INDEX = "stepIndex";

    private TraceMdc() {
    }

    /** 当前线程的 traceId; 没有则返回 null(后台任务/单测场景)。 */
    public static String traceId() {
        String value = MDC.get(TRACE_ID);
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 当前线程正在执行的步骤序号; 没有或非法则返回 null。 */
    public static Integer stepIndex() {
        String value = MDC.get(STEP_INDEX);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 绑定步骤序号; 由编排层在每步开始前调用, 供工具调用回填到同一步。 */
    public static void bindStepIndex(int stepIndex) {
        MDC.put(STEP_INDEX, String.valueOf(stepIndex));
    }

    /** 清理步骤序号; 必须在 finally 里调用, 防止线程复用串号。 */
    public static void clearStepIndex() {
        MDC.remove(STEP_INDEX);
    }
}
