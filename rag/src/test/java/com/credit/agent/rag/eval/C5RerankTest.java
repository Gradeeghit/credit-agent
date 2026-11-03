package com.credit.agent.rag.eval;

import com.credit.agent.rag.retrieval.RetrievedChunk;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * C4 验收: 同一份 65 条标注下, 对比 VECTOR 与 VECTOR + LLM Rerank 的 Recall@3。
 * 业务含义: PRD C4 要求"相对 C3 基线 Recall@3 提升 ≥ 5%; 若下降, 配置默认关闭并记录数据"——
 * 这个结论只能靠跑真实模型得出, 单测只能证明解析与降级正确。
 * <p>
 * 成本: 每条 query 一次 chat 调用(候选 20 条 × 200 字), 共 65 次; 打 {@code @Tag("live")},
 * 平时 {@code mvn test} 不跑。跑法:
 * <pre>
 * $env:OPENAI_API_KEY = '&lt;chat Key&gt;'
 * $env:EMBEDDING_API_KEY = '&lt;embedding Key&gt;'
 * mvn test -pl rag -am -Dtest=C5RerankTest -DfailIfNoTests=false "-Dsurefire.excludedGroups=noop"
 * </pre>
 */
@Tag("live")
class C5RerankTest {

    private static final int TOP_K = 3;
    private static final int CANDIDATES = 5;

    private static final String DEFAULT_BASE_URL =
            "https://ws-7ave3oec1mw2bccl.cn-beijing.maas.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_EMBEDDING_MODEL = "qwen3.7-text-embedding-flash";
    private static final String DEFAULT_CHAT_MODEL = "deepseek-v4.1-flash-1017";

    @Test
    @DisplayName("C4 对比: VECTOR vs VECTOR+RERANK 的 Recall@3 与 MRR")
    void comparesVectorWithRerank() {
        String embeddingKey = firstNonBlank(System.getenv("EMBEDDING_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String chatKey = firstNonBlank(System.getenv("RERANK_API_KEY"), System.getenv("OPENAI_API_KEY"));
        assumeTrue(embeddingKey != null, "缺少 EMBEDDING_API_KEY/OPENAI_API_KEY, 跳过 live 评测");
        assumeTrue(chatKey != null, "缺少 RERANK_API_KEY/OPENAI_API_KEY, 跳过 live 评测");

        String embeddingBaseUrl = firstNonBlank(System.getenv("EMBEDDING_BASE_URL"),
                System.getenv("OPENAI_BASE_URL"), DEFAULT_BASE_URL);
        String embeddingModel = firstNonBlank(System.getenv("EMBEDDING_MODEL"), DEFAULT_EMBEDDING_MODEL);
        String chatBaseUrl = firstNonBlank(System.getenv("RERANK_BASE_URL"),
                System.getenv("OPENAI_BASE_URL"), DEFAULT_BASE_URL);
        String chatModel = firstNonBlank(System.getenv("RERANK_MODEL"),
                System.getenv("OPENAI_MODEL"), DEFAULT_CHAT_MODEL);

        C5Dataset dataset = C5Dataset.load();
        C5EmbeddingClient embedder = new C5EmbeddingClient(embeddingBaseUrl, embeddingKey, embeddingModel);
        List<double[]> chunkVectors = embedder.embed(dataset.chunks().stream()
                .map(chunk -> chunk.section() + " " + chunk.content()).toList());
        List<String> queryTexts = dataset.queries().stream().map(EvalQuery::query).toList();
        List<double[]> vectors = embedder.embed(queryTexts);
        Map<String, double[]> queryVectors = new HashMap<>();
        for (int i = 0; i < queryTexts.size(); i++) {
            queryVectors.put(queryTexts.get(i), vectors.get(i));
        }

        Function<String, List<RetrievedChunk>> candidates = query ->
                vectorCandidates(dataset, chunkVectors, queryVectors.get(query), CANDIDATES);
        Function<String, List<Long>> baseline = query -> candidates.apply(query).stream()
                .limit(TOP_K).map(RetrievedChunk::chunkId).toList();

        // 用真实的重排实现(评测无 Spring 容器, 这里手工装配)
        Reranker reranker = buildReranker(chatBaseUrl, chatKey, chatModel);
        // 统计"重排是否真的改变了 top-k" —— 用来区分"生效但判断与向量一致"与"根本没生效"
        java.util.concurrent.atomic.AtomicInteger changed = new java.util.concurrent.atomic.AtomicInteger();
        Function<String, List<Long>> reranked = query -> {
            List<RetrievedChunk> base = candidates.apply(query);
            List<Long> afterRerank = reranker.rerank(query, base, TOP_K).stream()
                    .map(RetrievedChunk::chunkId).toList();
            List<Long> baselineTopK = base.stream().limit(TOP_K).map(RetrievedChunk::chunkId).toList();
            if (!afterRerank.equals(baselineTopK)) {
                changed.incrementAndGet();
            }
            return afterRerank;
        };

        EvalReport baselineReport = RecallEvaluator.evaluate("VECTOR", dataset.queries(), baseline, TOP_K);
        EvalReport rerankReport = RecallEvaluator.evaluate("VECTOR+RERANK", dataset.queries(), reranked, TOP_K);

        System.out.println("===== C4 重排对比 (65 条) =====");
        System.out.println(baselineReport.summary());
        System.out.println(rerankReport.summary());
        double delta = rerankReport.recallAtK() - baselineReport.recallAtK();
        System.out.println(String.format(Locale.ROOT,
                "提升: %+.4f (%+.2f%%) | MRR: %+.4f", delta,
                baselineReport.recallAtK() == 0 ? 0.0 : delta * 100 / baselineReport.recallAtK(),
                rerankReport.mrr() - baselineReport.mrr()));
        System.out.println(String.format(Locale.ROOT,
                "重排改变 top3 的 query 数: %d/%d (0 = 没生效或判断与向量完全一致)",
                changed.get(), dataset.queries().size()));
        rerankReport.missed().forEach(miss -> System.out.println("[C4] MISS " + miss.queryId()
                + " | " + miss.query() + " | gold=" + miss.gold() + " | top=" + miss.topIds()));

        assertEquals(dataset.queries().size(), baselineReport.total());
        assertTrue(baselineReport.recallAtK() > 0.0, "向量档 0 命中, 检查 Key/模型/维度");
        // 不硬断言"必须提升 5%": 提升与否是要如实记录的数据, 但崩盘说明实现有 bug
        assertTrue(rerankReport.recallAtK() >= baselineReport.recallAtK() - 0.05,
                "重排后 Recall@3 下降超过 5 个点, 应关闭 rerank 并排查 prompt/解析");
    }

    /** 评测内算余弦 top-N(不连库): 与 C5ThreeWayTest 同一套口径。 */
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

    /**
     * 按 RERANK_PROVIDER 选择实现(默认 dashscope)。
     * 业务含义: 两条路都要能评 —— dashscope 是生产默认(专用模型, 便宜且不用解析),
     * chat 是备选(让 chat 模型输出排列), 用同一份语料对比才知道该留哪个。
     */
    private static Reranker buildReranker(String baseUrl, String apiKey, String chatModel) {
        String provider = firstNonBlank(System.getenv("RERANK_PROVIDER"), "dashscope");
        if ("chat".equalsIgnoreCase(provider)) {
            return new LlmReranker(new C5ChatModel(baseUrl, apiKey, chatModel), record -> { },
                    true, "", CANDIDATES, 200);
        }
        return new DashScopeReranker(record -> { }, true, baseUrl, baseUrl, apiKey, apiKey,
                firstNonBlank(System.getenv("RERANK_MODEL"), "qwen3-rerank"), CANDIDATES, 200, 15000);
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
