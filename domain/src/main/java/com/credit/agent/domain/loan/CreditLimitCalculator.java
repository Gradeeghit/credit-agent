package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 理论额度计算(D2)。
 * 业务含义: 额度 = 收入 × 产品倍数, 并与产品单笔上限取小值, 是"能借多少"的确定性口径, 不交给 LLM。
 * <p>
 * 已确认口径(2026-10-30 本人拍板):
 * <ol>
 *   <li>YEARLY(经营贷)直接用「年流水 × 倍数」, 不做月均折算</li>
 *   <li>产品封顶在此完成: 结果与 product.maxAmount 取小值, 因此 D3 拿到的已是封顶后额度</li>
 *   <li>结果 HALF_UP 保留 2 位, 与 NUMERIC(18,2) 对齐</li>
 * </ol>
 * 验收样例: 月薪 20000 × 倍数 12 = 240000.00; 若产品上限 200000 则封顶为 200000.00
 */
public final class CreditLimitCalculator {

    private CreditLimitCalculator() {
    }

    /**
     * 计算理论授信额度(含产品上限封顶)。
     *
     * @param income          收入金额, 必须大于 0
     * @param basis           收入口径; 当前两种口径算法一致, 参数保留用于语义表达与后续折算
     * @param incomeMultiple  产品额度倍数, 必须大于 0
     * @param productMaxAmount 产品单笔上限, 必须大于 0
     * @return 理论额度, 单位元, 保留 2 位小数
     * @throws IllegalArgumentException 任一入参为 null 或非正数时抛出
     */
    public static BigDecimal theoreticalLimit(BigDecimal income, IncomeBasis basis,
                                              BigDecimal incomeMultiple, BigDecimal productMaxAmount) {
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("收入必须大于 0");
        }
        if (basis == null) {
            throw new IllegalArgumentException("收入口径不能为空");
        }
        if (incomeMultiple == null || incomeMultiple.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("额度倍数必须大于 0");
        }
        if (productMaxAmount == null || productMaxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("产品上限必须大于 0");
        }
        return income.multiply(incomeMultiple)
                .min(productMaxAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
