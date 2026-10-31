package com.credit.agent.tool.product;

import java.math.BigDecimal;

/**
 * 产品参数快照(E1 的返回体)。
 * 业务含义: 只把"算钱要用到的参数"给 LLM 看, 让它据此解释额度与费率;
 * 金额一律 BigDecimal, 且本类不含任何计算结果 —— 计算结果必须由 domain 规则引擎产出。
 *
 * @param id             产品 ID
 * @param code           产品编码, 如 P1
 * @param name           产品名称
 * @param baseRateRef    基准利率类型, 如 LPR
 * @param rateBp         利率加点(bp), 年化 = LPR + rateBp/10000
 * @param termMinMonths  最短期限(月)
 * @param termMaxMonths  最长期限(月)
 * @param incomeMultiple 收入倍数
 * @param maxAmount      单笔额度上限(元)
 * @param feeRate        手续费率, 小数形式(0.005 = 0.5%)
 * @param status         ON / OFF
 */
public record ProductInfo(
        Long id,
        String code,
        String name,
        String baseRateRef,
        Integer rateBp,
        Integer termMinMonths,
        Integer termMaxMonths,
        BigDecimal incomeMultiple,
        BigDecimal maxAmount,
        BigDecimal feeRate,
        String status) {
}
