package com.credit.agent.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 流水明细抽取单测: 只抽每笔入账并按月汇总, 解析失败返回空列表。 */
class StatementExtractorTest {

    @Test
    @DisplayName("正常 JSON: 按月汇总入账, 支出(负数)被忽略")
    void groupsInflowByMonth() {
        String text = "1) 结论: 正常\n4) 明细 {\"transactions\":["
                + "{\"date\":\"2026-01-05\",\"amount\":12000.00},"
                + "{\"date\":\"2026-01-20\",\"amount\":8000.00},"
                + "{\"date\":\"2026-02-03\",\"amount\":20000.00},"
                + "{\"date\":\"2026-02-10\",\"amount\":-5000.00}]}";

        List<BigDecimal> inflows = StatementExtractor.monthlyInflows(text);

        assertEquals(2, inflows.size());
        assertEquals(new BigDecimal("20000.00"), inflows.get(0));
        assertEquals(new BigDecimal("20000.00"), inflows.get(1));
    }

    @Test
    @DisplayName("兼容 ```json 围栏与字符串金额、'/' 分隔日期")
    void toleratesFencesAndStringAmounts() {
        String text = "```json\n{\"transactions\":[{\"date\":\"2026/03/01\",\"amount\":\"15000.50\"}]}\n```";

        List<BigDecimal> inflows = StatementExtractor.monthlyInflows(text);

        assertEquals(1, inflows.size());
        assertEquals(new BigDecimal("15000.50"), inflows.get(0));
    }

    @Test
    @DisplayName("非 JSON / 空文本 / 缺字段: 返回空列表, 不抛异常")
    void invalidInputIsEmpty() {
        assertTrue(StatementExtractor.monthlyInflows(null).isEmpty());
        assertTrue(StatementExtractor.monthlyInflows("没有明细").isEmpty());
        assertTrue(StatementExtractor.monthlyInflows("{\"transactions\":[{\"date\":\"bad\",\"amount\":1}]}").isEmpty());
        assertTrue(StatementExtractor.monthlyInflows("{\"other\":1}").isEmpty());
    }
}
