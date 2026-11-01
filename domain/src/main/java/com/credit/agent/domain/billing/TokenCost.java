package com.credit.agent.domain.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 模型调用成本计算(H3)。
 * 业务含义: 单笔审批成本必须可复算、可对账 —— 输入 token × 单价, 全程 BigDecimal;
 * 绝不允许在应用层用 double 乘单价(这是"算钱不用 AI"在可观测性上的延伸)。
 * 单价由配置注入(PRD §12 待确认), 本类不写死任何价格。
 */
public final class TokenCost {

    /** 成本精度: 与 ai_call_log.cost NUMERIC(12,6) 一致 */
    private static final int SCALE = 6;

    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private TokenCost() {
    }

    /**
     * 计算一次调用的成本(元)。
     *
     * @param promptTokens          输入 token
     * @param completionTokens      输出 token
     * @param pricePerKPrompt       输入单价(元/千 token), 不得为负
     * @param pricePerKCompletion   输出单价(元/千 token), 不得为负
     * @return 成本, 保留 6 位小数
     * @throws IllegalArgumentException token 为负或单价为空/为负
     */
    public static BigDecimal cost(long promptTokens, long completionTokens,
                                  BigDecimal pricePerKPrompt, BigDecimal pricePerKCompletion) {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("token 数不能为负");
        }
        requirePrice(pricePerKPrompt, "输入单价");
        requirePrice(pricePerKCompletion, "输出单价");
        BigDecimal promptCost = BigDecimal.valueOf(promptTokens)
                .multiply(pricePerKPrompt)
                .divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(completionTokens)
                .multiply(pricePerKCompletion)
                .divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
        return promptCost.add(completionCost).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePrice(BigDecimal price, String name) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(name + "不能为空或负数");
        }
    }
}
