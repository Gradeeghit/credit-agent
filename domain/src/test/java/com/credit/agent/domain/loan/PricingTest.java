package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 利率换算单测: LPR 3.45% + 100bp = 4.45%。 */
class PricingTest {

    @Test
    @DisplayName("LPR 0.0345 + 100bp → 0.044500; +250bp → 0.059500")
    void addsSpreadInBasisPoints() {
        assertEquals(new BigDecimal("0.044500"), Pricing.annualRate(new BigDecimal("0.0345"), 100));
        assertEquals(new BigDecimal("0.059500"), Pricing.annualRate(new BigDecimal("0.0345"), 250));
        assertEquals(new BigDecimal("0.034500"), Pricing.annualRate(new BigDecimal("0.0345"), 0));
    }

    @Test
    @DisplayName("LPR 或加点为空/为负直接拒绝")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Pricing.annualRate(null, 100));
        assertThrows(IllegalArgumentException.class, () -> Pricing.annualRate(new BigDecimal("-0.01"), 100));
        assertThrows(IllegalArgumentException.class, () -> Pricing.annualRate(new BigDecimal("0.0345"), null));
        assertThrows(IllegalArgumentException.class, () -> Pricing.annualRate(new BigDecimal("0.0345"), -1));
    }
}
