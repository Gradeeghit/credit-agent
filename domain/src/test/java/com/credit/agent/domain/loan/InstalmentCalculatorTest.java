package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D4 等额本息单测: PRD 验收值 1887.12、零利率退化、非法入参。 */
class InstalmentCalculatorTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    @Test
    @DisplayName("本金 10 万、年化 0.05、60 期 → 1887.12(±0.01)")
    void monthlyPaymentShouldMatchExpected() {
        BigDecimal payment = InstalmentCalculator.monthlyPayment(
                new BigDecimal("100000"), new BigDecimal("0.05"), 60);
        System.out.println(payment);

        assertTrue(payment.subtract(new BigDecimal("1887.12")).abs().compareTo(TOLERANCE) <= 0,
                "实际月供: " + payment);
    }

    @Test
    @DisplayName("年化为 0 时退化为等额本金: 120000 / 12 期 = 10000.00")
    void zeroRateShouldDegrade() {
        BigDecimal payment = InstalmentCalculator.monthlyPayment(
                new BigDecimal("120000"), BigDecimal.ZERO, 12);

        assertEquals(new BigDecimal("10000.00"), payment);
    }

    @Test
    @DisplayName("期数与本金非法时抛异常")
    void invalidInputShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> InstalmentCalculator.monthlyPayment(
                new BigDecimal("100000"), new BigDecimal("0.05"), 0));
        assertThrows(IllegalArgumentException.class, () -> InstalmentCalculator.monthlyPayment(
                BigDecimal.ZERO, new BigDecimal("0.05"), 12));
    }
}
