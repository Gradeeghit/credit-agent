package com.credit.agent.orchestration.runtime;

/**
 * 失败原因枚举(PRD §3.5 全 7 值)。
 * 业务含义: 审批失败必须能归因到具体环节, 而不是笼统一句"分析失败";
 * 这七个值同时是 approval_trace.failure_reason / approval_step.failure_reason 的 CHECK 约束取值。
 */
public enum FailureReason {

    /** 工具调用超时 */
    TOOL_TIMEOUT,

    /** 工具参数非法 */
    TOOL_PARAM_INVALID,

    /** 模型调用超时 */
    LLM_TIMEOUT,

    /** token 预算耗尽(F2) */
    BUDGET_EXCEEDED,

    /** 步数上限(F2) */
    MAX_STEPS,

    /** 调用了不存在的工具 */
    TOOL_NOT_FOUND,

    /** 检索降级(向量不可用或超时) */
    RETRIEVAL_DEGRADED
}
