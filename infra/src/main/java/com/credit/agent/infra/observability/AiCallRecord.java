package com.credit.agent.infra.observability;

/**
 * 一次 LLM 调用的计量记录(H3 的原始数据)。
 * 业务含义: 单笔审批成本必须能按 trace_id 聚合出来, 所以每次调用都要留一条:
 * 用的什么模型、吃了多少 prompt / completion token、花了多久。
 * 注意: cost 不在本类计算 —— 金额由 domain 的计价函数确定性算出, 不在这里用 double 乘单价。
 *
 * @param traceId         全链路追踪 ID, 成本聚合的唯一维度
 * @param model           模型标识, 取不到时填 unknown
 * @param promptTokens    输入 token
 * @param completionTokens 输出 token
 * @param latencyMs       调用耗时(毫秒)
 */
public record AiCallRecord(
        String traceId,
        String model,
        long promptTokens,
        long completionTokens,
        long latencyMs) {
}
