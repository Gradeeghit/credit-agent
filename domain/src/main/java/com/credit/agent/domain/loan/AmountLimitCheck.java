package com.credit.agent.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 申请金额校验结果(D3)。
 * 业务含义: 金额超过可批额度时, 系统只能"建议降额", 不能替人做拒绝决定;
 * 因此动作只有 PASS 与 ADJUST_AMOUNT, <b>刻意不提供 REJECT</b>
 * (PRD D3 验收: 超限时返回体 action 字段不得为 REJECT)。
 * <p>
 * 已确认口径(2026-10-30 本人拍板):
 * <ol>
 *   <li>封顶已在 D2 完成, 本类只与「可批额度」比较, 不重复与产品上限比较</li>
 *   <li>suggestedAmount 为可批额度<b>向下取整到万</b>, 不足 1 万时取可批额度原值</li>
 *   <li>申请金额恰好等于可批额度算 PASS(等于不算超)</li>
 * </ol>
 * 验收样例: 申请 30 万、可批额度 24 万 → 超出 60000、建议 240000、动作 ADJUST_AMOUNT
 */
public record AmountLimitCheck(
        LimitAction action,
        BigDecimal exceedAmount,
        BigDecimal suggestedAmount,
        String reason) {

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /** 处置动作: 只有"通过"与"建议降额"。 */
    public enum LimitAction {
        PASS,
        ADJUST_AMOUNT
    }

    /**
     * 校验申请金额是否超过可批额度。
     *
     * @param requestedAmount 客户申请的金额, 必须大于 0
     * @param approvableLimit 可批额度, 应为 D2 输出的封顶后额度, 不得为负
     * @throws IllegalArgumentException 申请金额非正或可批额度为负时抛出
     */
    public static AmountLimitCheck check(BigDecimal requestedAmount, BigDecimal approvableLimit) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("申请金额必须大于 0");
        }
        if (approvableLimit == null || approvableLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("可批额度不能为负");
        }

        BigDecimal requested = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (requested.compareTo(approvableLimit) <= 0) {
            return new AmountLimitCheck(LimitAction.PASS, BigDecimal.ZERO.setScale(2),
                    requested, "申请金额未超出可批额度");
        }
        BigDecimal exceed = requested.subtract(approvableLimit).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggested = roundDownToWan(approvableLimit);
        return new AmountLimitCheck(LimitAction.ADJUST_AMOUNT, exceed, suggested,
                "申请金额超出可批额度 " + exceed.toPlainString() + " 元, 建议降至 "
                        + suggested.toPlainString() + " 元");
    }

    /** 向下取整到万: 235000 → 230000; 不足 1 万时保留原值, 避免给出"建议 0 元"的荒谬结论。 */
    private static BigDecimal roundDownToWan(BigDecimal value) {
        BigDecimal floored = value.divide(TEN_THOUSAND, 0, RoundingMode.FLOOR)
                .multiply(TEN_THOUSAND)
                .setScale(2, RoundingMode.HALF_UP);
        return floored.compareTo(BigDecimal.ZERO) <= 0 ? value.setScale(2, RoundingMode.HALF_UP) : floored;
    }
}
