package com.credit.agent.rag.eval;

import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.RrfFusion;
import com.credit.agent.rag.retrieval.bm25.Bm25Document;
import com.credit.agent.rag.retrieval.bm25.Bm25Index;
import com.credit.agent.rag.retrieval.bm25.Bm25Params;
import com.credit.agent.rag.retrieval.bm25.JiebaTokenizer;
import com.credit.agent.rag.retrieval.rerank.DashScopeReranker;
import com.credit.agent.rag.retrieval.rerank.LlmReranker;
import com.credit.agent.rag.retrieval.rerank.Reranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * C5 三档同表: VECTOR / HYBRID(RRF) / VECTOR+RERANK, 虚构与真实两份语料各跑一遍。
 * 业务含义: C2/C3/C4 分在三个用例里各跑各的, 结论无法横向比较;
 * 这里同一批向量、同一份语料、统一候选宽度(20), 差异只来自检索策略本身。
 * <p>
 * 真实调 embedding + rerank(花钱), 故打 {@code @Tag("live")}, 默认 {@code mvn test} 不跑:
 * <pre>
 * $env:EMBEDDING_API_KEY='&lt;key&gt;'; $env:EMBEDDING_MODEL='qwen3.7-text-embedding-flash'
 * $env:RERANK_PROVIDER='dashscope'; $env:RERANK_MODEL='qwen3.7-text-rerank'
 * mvn test -pl rag -am -Dtest=C5ModesLiveTest "-Dsurefire.excludedGroups=noop" "-Dsurefire.failIfNoSpecifiedTests=false"
 * </pre>
 * 注意: rerank 模型名必须是该端点真实存在的专用模型(qwen3.7-text-rerank);
 * 名字填错时接口仍可能返回 200 但分数无区分度, 表现为"名次几乎不变", 本类会打印告警。
 */
@Tag("live")
class C5ModesLiveTest {

    private static final int TOP_K = 3;
    private static final int CANDIDATES = 20;
    private static final int RRF_K = 60;

    private static final String DEFAULT_BASE_URL =
            "https://ws-7ave3oec1mw2bccl.cn-beijing.maas.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_EMBEDDING_MODEL = "qwen3.7-text-embedding-flash";
    private static final String DEFAULT_CHAT_MODEL = "deepseek-v4.1-flash-1017";

    @Test
    @DisplayName("三档同表: 虚构 65 条 + 真实 62 条, 各跑 VECTOR / HYBRID / VECTOR+RERANK")
    void comparesThreeModes() {
        String embeddingKey = firstNonBlank(System.getenv("EMBEDDING_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String rerankKey = firstNonBlank(System.getenv("RERANK_API_KEY"), System.getenv("OPENAI_API_KEY"));
        assumeTrue(embeddingKey != null, "缺少 EMBEDDING_API_KEY/OPENAI_API_KEY, 跳过 live 评测");
        assumeTrue(rerankKey != null, "缺少 RERANK_API_KEY/OPENAI_API_KEY, 跳过 live 评测");

        String embeddingBaseUrl = firstNonBlank(System.getenv("EMBEDDING_BASE_URL"),
                System.getenv("OPENAI_BASE_URL"), DEFAULT_BASE_URL);
        String embeddingModel = firstNonBlank(System.getenv("EMBEDDING_MODEL"), DEFAULT_EMBEDDING_MODEL);
        String rerankBaseUrl = firstNonBlank(System.getenv("RERANK_BASE_URL"),
                System.getenv("OPENAI_BASE_URL"), DEFAULT_BASE_URL);
        String chatModel = firstNonBlank(System.getenv("RERANK_MODEL"),
                System.getenv("OPENAI_MODEL"), DEFAULT_CHAT_MODEL);

        C5EmbeddingClient embedder = new C5EmbeddingClient(embeddingBaseUrl, embeddingKey, embeddingModel);
        Reranker reranker = buildReranker(rerankBaseUrl, rerankKey, chatModel);
        // 注意不能用 || 连写: 短路会让第二份语料直接不跑
        boolean fictionalEffective = runModes("虚构语料 65 条", C5Dataset.load(), embedder, reranker);
        boolean realEffective = runModes("真实条文 92 条(3 份办法)", C5Dataset.loadReal(), embedder, reranker);

        assertTrue(fictionalEffective && realEffective,
                "有语料的 rerank 没有改变任何 top3, 检查 RERANK_PROVIDER/模型名/权限");
    }

    /** 跑一份语料的三档; 返回 rerank 是否真的产生过名次变化。 */
    private static boolean runModes(String title, C5Dataset dataset, C5EmbeddingClient embedder, Reranker reranker) {
        List<double[]> chunkVectors = embedder.embed(dataset.chunks().stream()
                .map(chunk -> chunk.section() + " " + chunk.content()).toList());
        Map<String, double[]> queryVectors = queryVectors(dataset, embedder);

        Function<String, List<RetrievedChunk>> candidates = query ->
                vectorCandidates(dataset, chunkVectors, queryVectors.get(query), CANDIDATES);
        JiebaTokenizer tokenizer = new JiebaTokenizer();
        Bm25Index bm25 = Bm25Index.build(dataset.chunks().stream()
                .map(chunk -> new Bm25Document(chunk.chunkId(),
                        tokenizer.tokenize(chunk.section() + " " + chunk.content())))
                .toList(), new Bm25Params(1.5, 0.75));

        Function<String, List<Long>> vector = query -> candidates.apply(query).stream()
                .limit(TOP_K).map(RetrievedChunk::chunkId).toList();
        Function<String, List<Long>> hybrid = query -> RrfFusion.fuse(Map.of(
                        "vector", candidates.apply(query).stream().map(RetrievedChunk::chunkId).toList(),
                        "bm25", bm25.search(tokenizer.tokenize(query), CANDIDATES).stream()
                                .map(Bm25Index.ScoredChunk::chunkId).toList()),
                RRF_K, CANDIDATES).stream().map(RrfFusion.FusedHit::chunkId).toList();
        AtomicInteger changed = new AtomicInteger();
        Function<String, List<Long>> reranked = query -> {
            List<RetrievedChunk> base = candidates.apply(query);
            List<Long> after = reranker.rerank(query, base, TOP_K).stream()
                    .map(RetrievedChunk::chunkId).toList();
            if (!after.equals(base.stream().limit(TOP_K).map(RetrievedChunk::chunkId).toList())) {
                changed.incrementAndGet();
            }
            return after;
        };

        EvalReport vectorReport = RecallEvaluator.evaluate("VECTOR", dataset.queries(), vector, TOP_K);
        EvalReport hybridReport = RecallEvaluator.evaluate("HYBRID", dataset.queries(), hybrid, TOP_K);
        EvalReport rerankReport = RecallEvaluator.evaluate("VECTOR+RERANK", dataset.queries(), reranked, TOP_K);

        System.out.println("===== C5 三档同表 | " + title + " | 候选宽 " + CANDIDATES
                + " | rerank=" + reranker.getClass().getSimpleName() + " =====");
        print(vectorReport, dataset);
        print(hybridReport, dataset);
        print(rerankReport, dataset);
        double delta = rerankReport.recallAtK() - vectorReport.recallAtK();
        System.out.println(String.format(Locale.ROOT,
                "rerank 改变 top3: %d/%d | 相对 VECTOR: %+.4f (%+.2f%%) | MRR %+.4f",
                changed.get(), dataset.queries().size(), delta,
                vectorReport.recallAtK() == 0 ? 0.0 : delta * 100 / vectorReport.recallAtK(),
                rerankReport.mrr() - vectorReport.mrr()));
        if (changed.get() == 0) {
            System.out.println("!! 警告: rerank 未改变任何名次 —— 上面的 RERANK 行不可用"
                    + "(通常是调用全部失败降级, 或模型名不对导致分数无区分度)");
        }

        assertEquals(dataset.queries().size(), vectorReport.total());
        assertTrue(vectorReport.recallAtK() > 0.0, "VECTOR 0 命中, 检查 Key/模型/维度");
        assertTrue(rerankReport.recallAtK() >= vectorReport.recallAtK() - 0.05,
                "重排后下降超过 5 个点, 应关闭 rerank 并排查 prompt/解析");
        return changed.get() > 0;
    }

    private static void print(EvalReport report, C5Dataset dataset) {
        EvalReport literal = rescore(report, dataset, false);
        EvalReport paraphrase = rescore(report, dataset, true);
        System.out.println(String.format(Locale.ROOT, "%-14s Recall@3=%.4f MRR=%.4f | 字面=%.4f 改写=%.4f",
                report.label(), report.recallAtK(), report.mrr(),
                literal.recallAtK(), paraphrase.recallAtK()));
        report.missed().forEach(miss -> System.out.println("[C5] " + report.label() + " MISS " + miss.queryId()
                + " | " + miss.query() + " | gold=" + miss.gold() + " | top=" + miss.topIds()));
    }

    private static EvalReport rescore(EvalReport report, C5Dataset dataset, boolean paraphrase) {
        return RecallEvaluator.evaluate(report.label(),
                dataset.queries().stream().filter(query -> query.isParaphrase() == paraphrase).toList(),
                query -> report.outcomes().stream().filter(outcome -> outcome.query().equals(query))
                        .findFirst().map(QueryOutcome::topIds).orElse(List.of()),
                report.k());
    }

    private static Map<String, double[]> queryVectors(C5Dataset dataset, C5EmbeddingClient embedder) {
        List<String> texts = dataset.queries().stream().map(EvalQuery::query).toList();
        List<double[]> vectors = embedder.embed(texts);
        Map<String, double[]> byText = new HashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            byText.put(texts.get(i), vectors.get(i));
        }
        return byText;
    }

    private static List<RetrievedChunk> vectorCandidates(C5Dataset dataset, List<double[]> chunkVectors,
                                                         double[] queryVector, int limit) {
        List<Map.Entry<Integer, Double>> scored = new ArrayList<>(chunkVectors.size());
        for (int i = 0; i < chunkVectors.size(); i++) {
            scored.add(Map.entry(i, C5EmbeddingClient.cosine(queryVector, chunkVectors.get(i))));
        }
        return scored.stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> {
                    EvalChunk chunk = dataset.chunks().get(entry.getKey());
                    return new RetrievedChunk(chunk.chunkId(), 1L, chunk.docName(), chunk.section(),
                            chunk.content(), chunk.page(), chunk.page(), entry.getValue());
                })
                .toList();
    }

    /** 按 RERANK_PROVIDER 选实现: dashscope=专用模型(生产默认), chat=让模型输出排列(备选)。 */
    private static Reranker buildReranker(String baseUrl, String apiKey, String chatModel) {
        String provider = firstNonBlank(System.getenv("RERANK_PROVIDER"), "dashscope");
        if ("chat".equalsIgnoreCase(provider)) {
            return new LlmReranker(new C5ChatModel(baseUrl, apiKey, chatModel), record -> { },
                    true, "", CANDIDATES, 200);
        }
        return new DashScopeReranker(record -> { }, true, baseUrl, baseUrl, apiKey, apiKey,
                firstNonBlank(System.getenv("RERANK_MODEL"), "qwen3.7-text-rerank"), CANDIDATES, 200, 15000);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
