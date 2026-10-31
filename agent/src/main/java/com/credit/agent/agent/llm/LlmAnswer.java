package com.credit.agent.agent.llm;

/**
 * 一次模型调用的结果。
 * 业务含义: 只带回文本与 token 用量 —— 文本用于组织理由, token 用于 F2 的预算终止;
 * 模型给出的任何数字都不得直接当作金额结论。
 *
 * @param text             模型输出文本
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 */
public record LlmAnswer(String text, long promptTokens, long completionTokens) {

    /** 本步消耗的总 token(F2 预算累计口径)。 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
