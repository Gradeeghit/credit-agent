package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 流水月均入账计算(算钱部分)。
 * 业务含义: 模型只负责从流水 PDF 里**抽出**每笔入账金额(读取材料), 汇总与平均必须在这里用确定性代码算,
 * 否则"流水月均"就成了模型拍出来的数字, 拿它去和收入证明比对等于用幻觉校验材料。
 * <p>
 * 口径(占位, 待本人确认): 只统计入账(amount &gt; 0), 月均 = 入账总额 / 有交易的月份数, HALF_UP 保留 2 位。
 */
public final class StatementMonthlyAverage {

    private StatementMonthlyAverage() {
    }

    /**
     * 计算流水月均入账。
     *
     * @param monthlyInflows 每个自然月的入账合计, 不含空月份; 必须大于 0
     * @return 月均入账, 2 位小数; 无月份数据时返回 null(不返回 0, 0 会被误读为"确实没钱")
     * @throws IllegalArgumentException 任一月份金额非正
     */
    public static BigDecimal average(List<BigDecimal> monthlyInflows) {
        if (monthlyInflows == null || monthlyInflows.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal inflow : monthlyInflows) {
            if (inflow == null || inflow.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("每月入账合计必须大于 0");
            }
            total = total.add(inflow);
        }
        return total.divide(BigDecimal.valueOf(monthlyInflows.size()), 2, RoundingMode.HALF_UP);
    }
}
