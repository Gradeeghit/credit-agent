package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** D2 理论额度单测: 倍数计算、产品封顶、YEARLY 不折算、非法入参。 */
class CreditLimitCalculatorTest {

    @Test
    @DisplayName("月薪 20000 × 倍数 12 = 240000.00, 未触顶")
    void monthlyIncomeTimesMultiple() {
        BigDecimal limit = CreditLimitCalculator.theoreticalLimit(
                new BigDecimal("20000"), IncomeBasis.MONTHLY, new BigDecimal("12.00"), new BigDecimal("300000"));

        assertEquals(new BigDecimal("240000.00"), limit);
    }

    @Test
    @DisplayName("YEARLY 直接乘倍数, 不做月均折算: 240000 × 24 超过上限后封顶 1000000.00")
    void yearlyIncomeIsNotConverted() {
        BigDecimal limit = CreditLimitCalculator.theoreticalLimit(
                new BigDecimal("240000"), IncomeBasis.YEARLY, new BigDecimal("24.00"), new BigDecimal("1000000"));

        assertEquals(new BigDecimal("1000000.00"), limit);
    }

    @Test
    @DisplayName("产品上限小于倍数结果时, 以产品上限封顶")
    void cappedByProductMaxAmount() {
        BigDecimal limit = CreditLimitCalculator.theoreticalLimit(
                new BigDecimal("20000"), IncomeBasis.MONTHLY, new BigDecimal("12.00"), new BigDecimal("200000"));

        assertEquals(new BigDecimal("200000.00"), limit);
    }

    @Test
    @DisplayName("收入非正抛异常")
    void nonPositiveIncomeShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> CreditLimitCalculator.theoreticalLimit(
                BigDecimal.ZERO, IncomeBasis.MONTHLY, new BigDecimal("12"), new BigDecimal("300000")));
    }
}
