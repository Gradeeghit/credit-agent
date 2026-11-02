package com.credit.agent.rag.eval;

import com.credit.agent.rag.retrieval.bm25.Bm25Document;
import com.credit.agent.rag.retrieval.bm25.Bm25Index;
import com.credit.agent.rag.retrieval.bm25.Bm25Params;
import com.credit.agent.rag.retrieval.bm25.JiebaTokenizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C5 真实语料基线: 2 份办法共 114 片 + 62 条标注
 * (《个人贷款管理办法》2024年第3号 52 条 + 《固定资产贷款管理办法》2024年第1号 62 条)。
 * 业务含义: 虚构语料只能验证"检索逻辑对不对", 真实条文才能验证"上线是否够用";
 * 本用例离线跑 BM25(不调模型), 阈值是回归护栏。
 */
class C5RealCorpusTest {

    private static final int TOP_K = 3;

    /** 回归护栏: 2 份办法 114 片下实测 0.6935, 留 4 个点余量; 跌破说明分词或语料被改坏。 */
    private static final double MIN_RECALL = 0.65;

    @Test
    @DisplayName("真实语料自检: 2 份办法共 114 片、62 条 query, gold 全部落在语料范围内")
    void datasetIsConsistent() {
        C5Dataset dataset = C5Dataset.loadReal();

        assertEquals(114, dataset.chunks().size());
        assertEquals(62, dataset.queries().size());
        Set<Long> corpusIds = dataset.chunks().stream().map(EvalChunk::chunkId).collect(Collectors.toSet());
        for (EvalQuery query : dataset.queries()) {
            assertTrue(corpusIds.containsAll(query.gold()),
                    "gold 不在语料里: " + query.id() + " -> " + query.gold());
        }
        assertTrue(dataset.chunks().stream().allMatch(chunk -> chunk.content().length() > 10),
                "条文正文不能为空");
    }

    @Test
    @DisplayName("真实语料 BM25 基线: 打印总体与分档 Recall@3, 守住回归阈值")
    void bm25Baseline() {
        C5Dataset dataset = C5Dataset.loadReal();
        JiebaTokenizer tokenizer = new JiebaTokenizer();
        Bm25Index index = Bm25Index.build(dataset.chunks().stream()
                .map(chunk -> new Bm25Document(chunk.chunkId(),
                        tokenizer.tokenize(chunk.section() + " " + chunk.content())))
                .toList(), new Bm25Params(1.5, 0.75));

        EvalReport report = RecallEvaluator.evaluate("BM25(real)", dataset.queries(),
                query -> index.search(tokenizer.tokenize(query), TOP_K).stream()
                        .map(Bm25Index.ScoredChunk::chunkId).toList(), TOP_K);

        System.out.println(String.format(Locale.ROOT, "[C5-real] %s", report.summary()));
        report.missed().forEach(miss -> System.out.println("[C5-real] MISS " + miss.queryId()
                + " | " + miss.query() + " | gold=" + miss.gold() + " | top=" + miss.topIds()));

        List<EvalQuery> literal = dataset.queries().stream().filter(q -> !q.isParaphrase()).toList();
        EvalReport literalReport = RecallEvaluator.evaluate("BM25(real)/literal", literal,
                query -> index.search(tokenizer.tokenize(query), TOP_K).stream()
                        .map(Bm25Index.ScoredChunk::chunkId).toList(), TOP_K);
        System.out.println(String.format(Locale.ROOT, "[C5-real] %s", literalReport.summary()));

        assertTrue(report.recallAtK() >= MIN_RECALL,
                "真实语料 BM25 跌破护栏: " + report.summary());
    }
}
