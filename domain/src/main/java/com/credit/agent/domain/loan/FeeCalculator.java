package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 手续费计算(D5)。
 * 业务含义: 放款前一次性收取, 口径为「实际放款额 × 产品手续费率」。
 * <p>
 * 已确认口径(2026-10-30 本人拍板):
 * <ol>
 *   <li>计费基数是<b>降额后的实际放款额</b>, 不是申请金额 —— 客户被降额时两者不同, 直接影响到手金额</li>
 *   <li>不做最低收费, 也不做收费封顶</li>
 *   <li>费率来自 product.fee_rate, 不在代码里写死; 结果 HALF_UP 保留 2 位</li>
 * </ol>
 * 验收样例: 200000 × 0.0050 = 1000.00
 */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    /**
     * 计算一次性手续费。
     *
     * @param actualLoanAmount 实际放款额(降额后金额), 必须大于 0
     * @param feeRate          手续费率小数形式(0.5% 传 0.0050), 取值 [0, 1)
     * @return 手续费, 保留 2 位小数(四舍五入)
     * @throws IllegalArgumentException 放款额非正或费率越界时抛出
     */
    public static BigDecimal fee(BigDecimal actualLoanAmount, BigDecimal feeRate) {
        if (actualLoanAmount == null || actualLoanAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("实际放款额必须大于 0");
        }
        if (feeRate == null || feeRate.compareTo(BigDecimal.ZERO) < 0
                || feeRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("手续费率必须落在 [0, 1) 区间");
        }
        return actualLoanAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }
}
