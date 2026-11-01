package com.credit.agent.infra.observability;

/**
 * 工具调用留痕的记录口。
 * 业务含义: tool 模块只负责"产生一次调用发生了什么", 是否落库、落哪张表由上层实现决定;
 * 实现方写失败必须自己吞掉 —— 留痕不能反过来阻断工具调用。
 */
@FunctionalInterface
public interface ToolCallRecorder {

    /** 空实现: 无追踪上下文(单测/离线调用)时用它兜底。 */
    ToolCallRecorder NOOP = record -> {
    };

    /**
     * 记录一次工具调用。
     *
     * @param record 追踪 ID、步序号、工具名、摘要与失败原因
     */
    void record(ToolCallRecord record);
}
