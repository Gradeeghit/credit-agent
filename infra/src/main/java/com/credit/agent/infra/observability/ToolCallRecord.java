package com.credit.agent.infra.observability;

/**
 * 一次工具调用的留痕(H1 的 Tool 段)。
 * 业务含义: 工具是唯一能给结论"找依据"的动作, 必须能回答"哪一步调了哪个工具、花了多久、拿到没有";
 * 参数与结果都只存截断摘要 —— prompt 里含客户敏感信息, 不能整段落库。
 *
 * @param traceId       所属审批追踪 ID
 * @param stepIndex     所属步骤序号; null 表示不在某个具体步骤内(此时不落库)
 * @param toolName      工具名, 如 searchPolicy / queryProduct
 * @param inputSummary  入参摘要(截断)
 * @param outputSummary 出参摘要(截断)
 * @param latencyMs     工具自身耗时(不含模型推理)
 * @param failureReason 失败原因, 取值与 approval_step.failure_reason 的 CHECK 一致; 成功为 null
 */
public record ToolCallRecord(
        String traceId,
        Integer stepIndex,
        String toolName,
        String inputSummary,
        String outputSummary,
        long latencyMs,
        String failureReason) {
}
