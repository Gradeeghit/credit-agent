package com.credit.agent.rag.eval;

import com.credit.agent.rag.retrieval.bm25.Bm25Document;
import com.credit.agent.rag.retrieval.bm25.Bm25Index;
import com.credit.agent.rag.retrieval.bm25.Bm25Params;
import com.credit.agent.rag.retrieval.bm25.JiebaTokenizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C5 基线: 50 条标注 query 在固定语料上的 BM25 Recall@3。
 * 业务含义: 这是决定"制度域要不要切 HYBRID"的对照基线 —— 向量档与 RRF 档必须跑同一份标注才有可比性;
 * 本用例只跑 BM25(不调模型、不花钱), 阈值是回归护栏而非目标值。
 * <p>
 * 2026-11-02 实测(65 条: 31 字面 + 34 改写): Recall@3 = 0.9231 (60/65), MRR = 0.8872;
 * 分档: 字面 1.0000(31/31) / 改写 0.8529(29/34) —— 差距就是向量路要补的空间。
 * 未命中 5 条: Q11 能不能贷五年、Q18 晚还一天会有什么后果、Q58 找谁反映、Q62 能借多少、Q65 给个说法
 * (共同点: 问法里没有条款原文的任何一个术语)。
 */
class C5Bm25BaselineTest {

    private static final int TOP_K = 3;

    /** 回归护栏: 实测 0.9231, 留 4 个点余量; 跌破说明 BM25 或语料被改坏了。 */
    private static final double MIN_RECALL = 0.88;

    @Test
    @DisplayName("评测集自检: 50 条 query、30 段语料, 每条 gold 都能在语料里找到")
    void datasetIsConsistent() {
        C5Dataset dataset = C5Dataset.load();

        assertEquals(30, dataset.chunks().size());
        assertEquals(65, dataset.queries().size());
        Set<Long> corpusIds = dataset.chunks().stream().map(EvalChunk::chunkId).collect(Collectors.toSet());
        for (EvalQuery query : dataset.queries()) {
            assertTrue(corpusIds.containsAll(query.gold()),
                    "gold 不在语料里: " + query.id() + " -> " + query.gold());
        }
    }

    @Test
    @DisplayName("BM25 基线: 打印总体与分档 Recall@3、未命中明细, 并守住回归阈值")
    void bm25Baseline() {
        C5Dataset dataset = C5Dataset.load();
        JiebaTokenizer tokenizer = new JiebaTokenizer();
        List<Bm25Document> documents = dataset.chunks().stream()
                .map(chunk -> new Bm25Document(chunk.chunkId(),
                        tokenizer.tokenize(chunk.section() + " " + chunk.content())))
                .toList();
        Bm25Index index = Bm25Index.build(documents, new Bm25Params(1.5, 0.75));
        Function<String, List<Long>> retriever = query -> index.search(tokenizer.tokenize(query), TOP_K).stream()
                .map(Bm25Index.ScoredChunk::chunkId).toList();

        EvalReport overall = RecallEvaluator.evaluate("BM25", dataset.queries(), retriever, TOP_K);
        EvalReport literal = RecallEvaluator.evaluate("BM25/literal", subset(dataset, false), retriever, TOP_K);
        EvalReport paraphrase = RecallEvaluator.evaluate("BM25/paraphrase", subset(dataset, true), retriever, TOP_K);

        System.out.println("[C5] " + overall.summary());
        System.out.println("[C5] " + literal.summary());
        System.out.println("[C5] " + paraphrase.summary());
        overall.missed().forEach(miss -> System.out.println("[C5] MISS " + miss.queryId()
                + " | " + miss.query() + " | gold=" + miss.gold() + " | top=" + miss.topIds()));

        assertTrue(overall.recallAtK() >= MIN_RECALL,
                "BM25 Recall@" + TOP_K + " 跌破护栏: " + overall.summary());
    }

    private static List<EvalQuery> subset(C5Dataset dataset, boolean paraphrase) {
        return dataset.queries().stream().filter(query -> query.isParaphrase() == paraphrase).toList();
    }
}
