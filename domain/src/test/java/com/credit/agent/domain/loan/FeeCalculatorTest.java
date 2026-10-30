package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** D5 手续费单测: 验收值 1000.00、尾差 HALF_UP、费率越界。 */
class FeeCalculatorTest {

    @Test
    @DisplayName("20 万 × 0.5% = 1000.00, BigDecimal 精度无损")
    void feeShouldBeExact() {
        BigDecimal fee = FeeCalculator.fee(new BigDecimal("200000.00"), new BigDecimal("0.0050"));

        assertEquals(new BigDecimal("1000.00"), fee);
    }

    @Test
    @DisplayName("尾差按 HALF_UP: 33333.33 × 0.0030 = 99.99999 → 100.00")
    void feeShouldRoundHalfUp() {
        BigDecimal fee = FeeCalculator.fee(new BigDecimal("33333.33"), new BigDecimal("0.0030"));

        assertEquals(new BigDecimal("100.00"), fee);
    }

    @Test
    @DisplayName("未做最低收费: 算出 0.01 就返回 0.01, 不抬到最低值")
    void noMinimumCharge() {
        assertEquals(new BigDecimal("0.01"),
                FeeCalculator.fee(new BigDecimal("100.00"), new BigDecimal("0.0001")));
    }

    @Test
    @DisplayName("费率为 1 或放款额非正时抛异常")
    void invalidInputShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FeeCalculator.fee(new BigDecimal("200000"), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> FeeCalculator.fee(BigDecimal.ZERO, new BigDecimal("0.0050")));
    }
}
