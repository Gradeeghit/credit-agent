package com.credit.agent.rag.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 指标口径: 命中窗口、名次取第一条 gold、未命中记 0, 都不许含糊。 */
class RecallEvaluatorTest {

    private static EvalQuery query(String id, long gold) {
        return new EvalQuery(id, "问题" + id, List.of(gold), "literal");
    }

    @Test
    @DisplayName("命中/未命中: Recall 与 MRR 按名次算, 名次超窗口算未命中")
    void computesRecallAndMrr() {
        Map<String, List<Long>> ranking = Map.of(
                "问题Q1", List.of(7L, 1L, 2L),   // gold=1 在第 2 名
                "问题Q2", List.of(3L, 4L, 5L),   // gold=2 不在前 3
                "问题Q3", List.of(2L, 9L, 9L));  // gold=2 在第 1 名
        List<EvalQuery> queries = List.of(query("Q1", 1), query("Q2", 2), query("Q3", 2));

        EvalReport report = RecallEvaluator.evaluate("UNIT", queries, ranking::get, 3);

        assertEquals(2, report.hits());
        assertEquals(2.0 / 3, report.recallAtK(), 1e-9);
        // (1/2 + 0 + 1/1) / 3
        assertEquals(0.5, report.mrr(), 1e-9);
        assertEquals(List.of("Q2"), report.missed().stream().map(QueryOutcome::queryId).toList());
    }

    @Test
    @DisplayName("多条 gold: 命中任意一条即算召回, 名次取第一条出现的")
    void multipleGoldCountsAsHit() {
        EvalQuery multi = new EvalQuery("Q9", "问题Q9", List.of(5L, 6L), "paraphrase");

        EvalReport report = RecallEvaluator.evaluate("UNIT", List.of(multi),
                q -> List.of(1L, 2L, 6L), 3);

        assertEquals(1, report.hits());
        assertEquals(1.0 / 3, report.mrr(), 1e-9);
    }

    @Test
    @DisplayName("非法输入直接拒绝, 不返回一份看起来正常的报告")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class,
                () -> RecallEvaluator.evaluate("UNIT", List.of(), q -> List.of(), 3));
        assertThrows(IllegalArgumentException.class,
                () -> RecallEvaluator.evaluate("UNIT", List.of(query("Q1", 1)), q -> List.of(), 0));
    }

    @Test
    @DisplayName("检索返回空: 全部未命中, MRR=0, 不抛异常")
    void emptyRankingIsZeroScore() {
        EvalReport report = RecallEvaluator.evaluate("UNIT", List.of(query("Q1", 1)), q -> List.of(), 3);

        assertEquals(0, report.hits());
        assertEquals(0.0, report.recallAtK(), 1e-9);
        assertEquals(0.0, report.mrr(), 1e-9);
        assertTrue(report.summary().contains("Recall@3=0.0000"));
    }
}
