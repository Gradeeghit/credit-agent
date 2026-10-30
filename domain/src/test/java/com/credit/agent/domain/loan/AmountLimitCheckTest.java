package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D3 超限判定单测: 未超、超限降额、整万取整、边界等于、非法入参。 */
class AmountLimitCheckTest {

    @Test
    @DisplayName("未超限: PASS, 超出额为 0, 建议金额等于申请金额")
    void withinLimitShouldPass() {
        AmountLimitCheck result = AmountLimitCheck.check(new BigDecimal("200000"), new BigDecimal("240000"));

        assertEquals(AmountLimitCheck.LimitAction.PASS, result.action());
        assertEquals(new BigDecimal("0.00"), result.exceedAmount());
        assertEquals(new BigDecimal("200000.00"), result.suggestedAmount());
    }

    @Test
    @DisplayName("超限: 申请 30 万 / 可批 24 万 → 超出 60000, 建议 240000")
    void overLimitShouldSuggestAmount() {
        AmountLimitCheck result = AmountLimitCheck.check(new BigDecimal("300000"), new BigDecimal("240000"));

        assertEquals(AmountLimitCheck.LimitAction.ADJUST_AMOUNT, result.action());
        assertEquals(new BigDecimal("60000.00"), result.exceedAmount());
        assertEquals(new BigDecimal("240000.00"), result.suggestedAmount());
    }

    @Test
    @DisplayName("可批额度非整万时向下取整: 235000 → 建议 230000")
    void suggestedShouldRoundDownToTenThousand() {
        AmountLimitCheck result = AmountLimitCheck.check(new BigDecimal("300000"), new BigDecimal("235000"));

        assertEquals(new BigDecimal("230000.00"), result.suggestedAmount());
        assertTrue(result.reason().contains("230000.00"));
    }

    @Test
    @DisplayName("申请金额恰好等于可批额度算 PASS(等于不算超)")
    void equalAmountShouldPass() {
        assertEquals(AmountLimitCheck.LimitAction.PASS,
                AmountLimitCheck.check(new BigDecimal("240000"), new BigDecimal("240000")).action());
    }

    @Test
    @DisplayName("可批额度不足 1 万时不给出 0 元建议, 直接取原值")
    void tinyLimitShouldKeepOriginal() {
        AmountLimitCheck result = AmountLimitCheck.check(new BigDecimal("20000"), new BigDecimal("8500"));

        assertEquals(new BigDecimal("8500.00"), result.suggestedAmount());
    }

    @Test
    @DisplayName("申请金额非正抛异常")
    void nonPositiveRequestShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> AmountLimitCheck.check(BigDecimal.ZERO, new BigDecimal("240000")));
    }
}
