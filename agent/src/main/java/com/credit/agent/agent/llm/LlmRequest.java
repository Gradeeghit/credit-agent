package com.credit.agent.agent.llm;

import java.util.List;

/**
 * 一次模型调用请求。
 * 业务含义: trace_id 是成本聚合与回放的唯一维度, 必须随每次调用传下去;
 * systemPrompt 放域角色与硬约束(只准引用材料与制度, 不准算钱), userPrompt 放本步要回答的问题;
 * toolNames 决定"本次只挂哪几个工具"(F3 分域降载); forceTool 决定"本次是否必须调用工具"。
 *
 * @param traceId      全链路追踪 ID
 * @param systemPrompt 系统提示词, 可为空
 * @param userPrompt   本轮问题, 不能为空
 * @param toolNames    本域允许挂载的工具名; <b>null 表示全量</b>(旧调用方语义), 空列表表示一个都不挂
 * @param forceTool    是否强制模型必须发一次工具调用(制度域用); 默认 false, 且本域无工具时无效
 */
public record LlmRequest(
        String traceId,
        String systemPrompt,
        String userPrompt,
        List<String> toolNames,
        boolean forceTool) {

    /** 不限定工具子集、不强制工具的调用(网关按全量挂载), 供不区分域的调用方使用。 */
    public LlmRequest(String traceId, String systemPrompt, String userPrompt) {
        this(traceId, systemPrompt, userPrompt, null, false);
    }

    /** 只限定工具子集、不强制工具的调用。 */
    public LlmRequest(String traceId, String systemPrompt, String userPrompt, List<String> toolNames) {
        this(traceId, systemPrompt, userPrompt, toolNames, false);
    }

    public LlmRequest {
        toolNames = toolNames == null ? null : List.copyOf(toolNames);
    }
}
