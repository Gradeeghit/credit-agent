package com.credit.agent.agent.llm;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * ChatResponse 到内部契约的映射。
 * 业务含义: 不同提供方(DeepSeek/通义)对 usage 的填充并不一致, 取不到 token 时必须退化为 0 而不是抛错,
 * 否则一次统计字段缺失就会把整笔审批打成失败。
 */
public final class LlmResponses {

    private static final String UNKNOWN_MODEL = "unknown";

    private LlmResponses() {
    }

    /**
     * 抽取文本与 token 用量。
     *
     * @param response 模型原始响应
     * @return 文本(空串兜底)与 token(缺失按 0)
     * @throws LlmCallException 响应为空或没有结果
     */
    public static LlmAnswer toAnswer(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new LlmCallException("模型返回为空");
        }
        String text = response.getResult().getOutput().getText();
        Usage usage = usageOf(response);
        return new LlmAnswer(text == null ? "" : text, tokens(usage, true), tokens(usage, false));
    }

    /** 取模型标识; 取不到统一记 unknown, 避免成本报表出现空分组。 */
    public static String modelOf(ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null) {
                return UNKNOWN_MODEL;
            }
            String model = response.getMetadata().getModel();
            return (model == null || model.isBlank()) ? UNKNOWN_MODEL : model;
        } catch (RuntimeException e) {
            return UNKNOWN_MODEL;
        }
    }

    private static Usage usageOf(ChatResponse response) {
        try {
            return response.getMetadata() == null ? null : response.getMetadata().getUsage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long tokens(Usage usage, boolean prompt) {
        if (usage == null) {
            return 0;
        }
        try {
            Number value = prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
            return value == null ? 0 : Math.max(0, value.longValue());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
