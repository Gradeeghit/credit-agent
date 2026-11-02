package com.credit.agent.rag.retrieval.bm25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BM25 单测(PRD C1 验收: 3 篇文档手工语料, 分值可与手算结果比对, 误差 &lt; 0.001)。
 * <p>
 * 手工语料与手算过程(按 Lucene 版 IDF = ln(1 + (N-df+0.5)/(df+0.5)), k1=1.5, b=0.75):
 * <pre>
 *   d1 = 逾期 三次 算 不良   (长度 4)
 *   d2 = 逾期 一次           (长度 2)
 *   d3 = 额度 上限 三十万     (长度 3)
 *   query = 逾期 不良
 *
 *   N = 3, avgdl = 9/3 = 3.0
 *   df(逾期)=2 → IDF = ln(1.6)      = 0.4700
 *   df(不良)=1 → IDF = ln(2.66667)  = 0.9808
 *
 *   d1 长度归一化 = 0.25 + 0.75×(4/3) = 1.25  → tf 项 = 2.5/(1+1.5×1.25) = 2.5/2.875 = 0.86957
 *      score = (0.4700 + 0.9808) × 0.86957 = 1.2616
 *   d2 长度归一化 = 0.25 + 0.75×(2/3) = 0.75  → tf 项 = 2.5/(1+1.5×0.75) = 2.5/2.125 = 1.17647
 *      score = 0.4700 × 1.17647 = 0.5529
 *   d3 不含查询词 → 0
 * </pre>
 * 若你实现时选原始版 IDF(ln((N-df+0.5)/(df+0.5)), 可能为负), 把下面两个期望值按同样方法重算即可。
 */
class Bm25IndexTest {

    private static final Bm25Params PARAMS = Bm25Params.DEFAULT;

    private static final Bm25Document D1 = new Bm25Document(1L, List.of("逾期", "三次", "算", "不良"));
    private static final Bm25Document D2 = new Bm25Document(2L, List.of("逾期", "一次"));
    private static final Bm25Document D3 = new Bm25Document(3L, List.of("额度", "上限", "三十万"));

    private static final List<String> QUERY = List.of("逾期", "不良");

    @Test
    @DisplayName("建索引时统计语料规模、平均长度与文档频次(同一文档重复词只计一次 df)")
    void buildAggregatesStatistics() {
        Bm25Index index = Bm25Index.build(List.of(D1, D2, D3), PARAMS);

        assertEquals(3, index.documentCount());
        assertEquals(3.0, index.averageLength(), 1e-9);
        assertEquals(2, index.documentFrequency("逾期"));
        assertEquals(1, index.documentFrequency("不良"));
        assertEquals(0, index.documentFrequency("不存在"));
    }

    @Test
    @DisplayName("重复词在同一文档内只算一个 df")
    void duplicateTermsCountOnceInDf() {
        Bm25Document repeated = new Bm25Document(9L, List.of("逾期", "逾期", "逾期"));

        Bm25Index index = Bm25Index.build(List.of(repeated, D3), PARAMS);

        assertEquals(1, index.documentFrequency("逾期"));
        assertEquals(2, index.documentCount());
    }

    @Test
    @DisplayName("空语料与非法参数直接拒绝")
    void rejectInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Bm25Index.build(List.of(), PARAMS));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Params(0, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Params(1.5, 1.5));
    }

    @Test
    @DisplayName("手算比对: d1=1.2616, d2=0.5529, d3=0")
    void scoreMatchesHandCalculation() {
        Bm25Index index = Bm25Index.build(List.of(D1, D2, D3), PARAMS);

        assertEquals(0.4700, index.idf("逾期"), 0.001);
        assertEquals(0.9808, index.idf("不良"), 0.001);
        assertEquals(1.2616, index.score(D1, QUERY), 0.001);
        assertEquals(0.5529, index.score(D2, QUERY), 0.001);
        assertEquals(0.0, index.score(D3, QUERY), 0.001);
    }

    @Test
    @DisplayName("检索按分数降序, 不含查询词的切片被丢弃, limit 生效")
    void searchRanksByScoreAndLimits() {
        Bm25Index index = Bm25Index.build(List.of(D1, D2, D3), PARAMS);

        List<Bm25Index.ScoredChunk> hits = index.search(QUERY, 5);

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).chunkId());
        assertEquals(2L, hits.get(1).chunkId());
        assertEquals(1, index.search(QUERY, 1).size());
    }
}
