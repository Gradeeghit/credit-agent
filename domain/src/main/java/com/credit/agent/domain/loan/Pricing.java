package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 年化利率口径换算。
 * 业务含义: 产品只存"LPR + 加点(bp)", 真正参与月供计算的是年化小数(如 0.0445), 这一步必须确定性可复算,
 * 不允许散落在编排层用 double 拼。LPR 取值由配置注入(PRD §12-5 待定), 本类不写死任何基准值。
 * <p>
 * 换算: 年化 = LPR + rateBp / 10000, 保留 6 位小数(足够喂给等额本息公式, 又不引入浮点)。
 */
public final class Pricing {

    private static final BigDecimal BP_PER_UNIT = new BigDecimal("10000");

    private static final int SCALE = 6;

    private Pricing() {
    }

    /**
     * 把"LPR + 加点"换算成年化利率小数。
     *
     * @param lpr    基准利率小数形式(3.45% 传 0.0345), 不得为负
     * @param rateBp 加点, 单位 bp(100bp = 1%), 不得为负
     * @return 年化利率小数, 保留 6 位
     * @throws IllegalArgumentException LPR 或加点为空/为负
     */
    public static BigDecimal annualRate(BigDecimal lpr, Integer rateBp) {
        if (lpr == null || lpr.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("LPR 不能为空或负数");
        }
        if (rateBp == null || rateBp < 0) {
            throw new IllegalArgumentException("利率加点不能为空或负数");
        }
        BigDecimal spread = BigDecimal.valueOf(rateBp).divide(BP_PER_UNIT, SCALE, RoundingMode.HALF_UP);
        return lpr.add(spread).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
