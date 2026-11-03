package com.credit.agent.rag.retrieval.rerank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * C4 重排结果解析: 模型输出不可信, 必须把围栏/解释/越界/漏项全部消化成一个合法排列。
 */
class RerankOrderParserTest {

    @Test
    @DisplayName("标准 JSON: 编号从 1 起, 转成 0 起下标")
    void parsesPlainJson() {
        assertEquals(List.of(2, 0, 1), RerankOrderParser.parse("{\"order\":[3,1,2]}", 3));
    }

    @Test
    @DisplayName("带代码围栏与前后解释: 只取首个 { 到末个 } 之间的内容")
    void ignoresFenceAndPreamble() {
        String text = "好的, 排序如下:\n```json\n{\"order\":[2,1,3]}\n```\n以上。";
        assertEquals(List.of(1, 0, 2), RerankOrderParser.parse(text, 3));
    }

    @Test
    @DisplayName("越界编号与重复编号被丢弃, 不产生新片段")
    void dropsOutOfRangeAndDuplicates() {
        // 0 与 9 越界, 两个 2 重复 -> 只剩 [2,1], 其余按原顺序补齐
        assertEquals(List.of(1, 0, 2), RerankOrderParser.parse("{\"order\":[2,2,0,9,1]}", 3));
    }

    @Test
    @DisplayName("模型漏掉的编号按原顺序补到末尾, 保证长度恒等于候选数")
    void appendsMissedInOriginalOrder() {
        assertEquals(List.of(2, 0, 1, 3), RerankOrderParser.parse("{\"order\":[3]}", 4));
    }

    @Test
    @DisplayName("没有 JSON 对象 -> null, 由调用方降级为原顺序")
    void nullWhenNoJson() {
        assertNull(RerankOrderParser.parse("无法判断", 3));
        assertNull(RerankOrderParser.parse(null, 3));
        assertNull(RerankOrderParser.parse("  ", 3));
    }

    @Test
    @DisplayName("有 JSON 但没有 order 数组, 或编号全部非法 -> null")
    void nullWhenOrderInvalid() {
        assertNull(RerankOrderParser.parse("{\"result\":[1,2]}", 3));
        assertNull(RerankOrderParser.parse("{\"order\":[0,99]}", 3));
        assertNull(RerankOrderParser.parse("{\"order\":\"3,1,2\"}", 3));
    }

    @Test
    @DisplayName("候选数为 0 时返回空列表, 不解析")
    void emptyWhenNoCandidates() {
        assertEquals(List.of(), RerankOrderParser.parse("{\"order\":[1]}", 0));
    }
}
