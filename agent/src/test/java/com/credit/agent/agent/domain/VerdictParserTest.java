package com.credit.agent.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 文本 → 结论枚举单测: 抽不到明确结论时一律按需补材料, 不放过。 */
class VerdictParserTest {

    @Test
    @DisplayName("缺材料关键词优先于异常关键词")
    void needMoreInfoHasPriority() {
        assertEquals(DomainVerdict.NEED_MORE_INFO, VerdictParser.parse("1) 需补材料: 缺少收入证明\n2) 另有异常"));
        assertEquals(DomainVerdict.NEED_MORE_INFO, VerdictParser.parse("待补: 身份证反面"));
    }

    @Test
    @DisplayName("异常/高风险归类为 ABNORMAL")
    void abnormalKeywords() {
        assertEquals(DomainVerdict.ABNORMAL, VerdictParser.parse("1) 结论: 异常, 存在逾期"));
        assertEquals(DomainVerdict.ABNORMAL, VerdictParser.parse("信息不一致"));
    }

    @Test
    @DisplayName("空文本与正常描述: 空 → 需补, 正常 → NORMAL")
    void blankAndNormal() {
        assertEquals(DomainVerdict.NEED_MORE_INFO, VerdictParser.parse(""));
        assertEquals(DomainVerdict.NEED_MORE_INFO, VerdictParser.parse(null));
        assertEquals(DomainVerdict.NORMAL, VerdictParser.parse("1) 结论: 正常 2) 依据见引用"));
    }
}
