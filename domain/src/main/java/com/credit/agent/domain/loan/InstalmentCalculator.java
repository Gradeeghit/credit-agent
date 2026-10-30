package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 月供计算(D4, 等额本息)。
 * 业务含义: 月供是还款能力判断的核心数字, 必须确定性可复算, 不允许 LLM 生成。
 * <p>
 * 公式(已确认口径 2026-10-30 本人拍板, 利率一律传小数字面量如 0.05):
 * <pre>
 *   r = 年化利率 / 12
 *   月供 = P × r × (1+r)^n / ((1+r)^n − 1)
 * </pre>
 * 精度约定: 中间过程用 34 位 MathContext 不舍入, 仅最终结果 HALF_UP 保留 2 位;
 * 全程 BigDecimal, 不出现 double(否则 D7 不成立)。
 * 退化分支: 年化利率为 0 时按等额本金均摊, 月供 = 本金 / 期数。
 * 验收样例: 本金 100000、年化 0.05、60 期 → 1887.12(±0.01)
 */
public final class InstalmentCalculator {

    /** 中间计算精度: 足以消除 60 期复利放大的舍入误差 */
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private InstalmentCalculator() {
    }

    /**
     * 计算等额本息月供。
     *
     * @param principal       贷款本金, 必须大于 0
     * @param annualRateRatio 年化利率小数形式(5% 传 0.05), 不得为负
     * @param months          期数(月), 必须大于 0
     * @return 月供, 保留 2 位小数(四舍五入)
     * @throws IllegalArgumentException 本金非正、利率为负或期数非正时抛出
     */
    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRateRatio, int months) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("本金必须大于 0");
        }
        if (annualRateRatio == null || annualRateRatio.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("年化利率不能为负");
        }
        if (months <= 0) {
            throw new IllegalArgumentException("期数必须大于 0");
        }
        if (annualRateRatio.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyRate = annualRateRatio.divide(MONTHS_PER_YEAR, MC);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, MC);
        BigDecimal numerator = principal.multiply(monthlyRate, MC).multiply(growth, MC);
        BigDecimal denominator = growth.subtract(BigDecimal.ONE, MC);
        return numerator.divide(denominator, MC).setScale(2, RoundingMode.HALF_UP);
    }
}
