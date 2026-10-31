package com.credit.agent.agent.llm;

/**
 * 一次模型调用请求。
 * 业务含义: trace_id 是成本聚合与回放的唯一维度, 必须随每次调用传下去;
 * systemPrompt 放域角色与硬约束(只准引用材料与制度, 不准算钱), userPrompt 放本步要回答的问题。
 *
 * @param traceId      全链路追踪 ID
 * @param systemPrompt 系统提示词, 可为空
 * @param userPrompt   本轮问题, 不能为空
 */
public record LlmRequest(String traceId, String systemPrompt, String userPrompt) {
}
