package com.credit.agent.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 流水月均单测: 只算总数除以月份数, 没有数据返回 null 而不是 0。 */
class StatementMonthlyAverageTest {

    @Test
    @DisplayName("三个月合计 60000 → 月均 20000.00(两位小数)")
    void averagesOverMonths() {
        BigDecimal avg = StatementMonthlyAverage.average(List.of(
                new BigDecimal("18000.00"), new BigDecimal("21000.00"), new BigDecimal("21000.00")));

        assertEquals(new BigDecimal("20000.00"), avg);
    }

    @Test
    @DisplayName("除不尽时 HALF_UP 保留两位")
    void roundsHalfUp() {
        BigDecimal avg = StatementMonthlyAverage.average(List.of(
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("10001.00")));

        assertEquals(new BigDecimal("10000.33"), avg);
    }

    @Test
    @DisplayName("无数据返回 null; 出现非正金额直接拒绝(不静默省略)")
    void nullWhenEmptyAndRejectsNonPositive() {
        assertNull(StatementMonthlyAverage.average(null));
        assertNull(StatementMonthlyAverage.average(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> StatementMonthlyAverage.average(List.of(new BigDecimal("100"), BigDecimal.ZERO)));
    }
}
