package com.credit.agent.domain.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 成本计算单测: 可手算核对, 且不接受负 token / 非法单价。 */
class TokenCostTest {

    @Test
    @DisplayName("2000 输入 + 500 输出, 单价 2 元/千与 8 元/千 → 4.00 + 4.00 = 8.000000")
    void computesCostByThousandTokens() {
        BigDecimal cost = TokenCost.cost(2000, 500,
                new BigDecimal("2.00"), new BigDecimal("8.00"));

        assertEquals(new BigDecimal("8.000000"), cost);
    }

    @Test
    @DisplayName("零 token → 0; 负 token / 负单价直接拒绝")
    void edgeCases() {
        assertEquals(new BigDecimal("0.000000"), TokenCost.cost(0, 0,
                new BigDecimal("2.00"), new BigDecimal("8.00")));
        assertThrows(IllegalArgumentException.class, () -> TokenCost.cost(-1, 0,
                new BigDecimal("2.00"), new BigDecimal("8.00")));
        assertThrows(IllegalArgumentException.class, () -> TokenCost.cost(1, 1, null, new BigDecimal("8.00")));
        assertThrows(IllegalArgumentException.class, () -> TokenCost.cost(1, 1,
                new BigDecimal("2.00"), new BigDecimal("-1")));
    }

    @Test
    @DisplayName("embedding 只有输入 token: 10000 token × 0.000125 元/千 = 0.001250")
    void embeddingCostUsesInputOnly() {
        assertEquals(new BigDecimal("0.001250"),
                TokenCost.embeddingCost(10000, new BigDecimal("0.000125")));
    }

    @Test
    @DisplayName("embedding 的负 token 与空单价直接拒绝")
    void embeddingEdgeCases() {
        assertThrows(IllegalArgumentException.class,
                () -> TokenCost.embeddingCost(-1, new BigDecimal("0.000125")));
        assertThrows(IllegalArgumentException.class, () -> TokenCost.embeddingCost(1, null));
    }
}
