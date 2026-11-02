package com.credit.agent.rag.eval;

import com.credit.agent.rag.retrieval.RrfFusion;
import com.credit.agent.rag.retrieval.bm25.Bm25Document;
import com.credit.agent.rag.retrieval.bm25.Bm25Index;
import com.credit.agent.rag.retrieval.bm25.Bm25Params;
import com.credit.agent.rag.retrieval.bm25.JiebaTokenizer;
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
 * C5 三档对比: VECTOR / BM25 / HYBRID(RRF k=60) 跑同一份标注。
 * 业务含义: "制度域要不要从 VECTOR 切 HYBRID"必须由这份对比决定, 不凭感觉。
 * <p>
 * 跑两份语料: ① 虚构 30 段 + 65 条 query(措辞规整); ② **真实条文** 2 份办法共 114 片 + 62 条 query
 * (《个人贷款管理办法》52 条 + 《固定资产贷款管理办法》62 条)。
 * 真实语料上 BM25 只有 0.6935(改写档 0.4194), 才能看出向量到底补了多少。
 * <p>
 * 会**真实调用 embedding 接口**(花钱、要网络), 故打 {@code @Tag("live")}: 默认 {@code mvn test} 不跑;
 * 需要时 {@code mvn test -pl rag -am -Dtest=C5ThreeWayTest -Dsurefire.excludedGroups=noop}。
 */
@Tag("live")
class C5ThreeWayTest {

    private static final int TOP_K = 3;
    private static final int CANDIDATES = 20;
    private static final int RRF_K = 60;

    private static final String DEFAULT_BASE_URL =
            "https://ws-7ave3oec1mw2bccl.cn-beijing.maas.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_MODEL = "qwen3.7-text-embedding-flash";

    @Test
    @DisplayName("三档对比: 虚构语料与真实条文语料各跑一遍 VECTOR / BM25 / HYBRID")
    void comparesThreeRetrievers() {
        String apiKey = firstNonBlank(System.getenv("EMBEDDING_API_KEY"), System.getenv("OPENAI_API_KEY"));
        assumeTrue(apiKey != null, "缺少 EMBEDDING_API_KEY/OPENAI_API_KEY, 跳过 live 评测");
        String baseUrl = firstNonBlank(System.getenv("EMBEDDING_BASE_URL"), System.getenv("OPENAI_BASE_URL"),
                DEFAULT_BASE_URL);
        String model = firstNonBlank(System.getenv("EMBEDDING_MODEL"), DEFAULT_MODEL);
        C5EmbeddingClient client = new C5EmbeddingClient(baseUrl, apiKey, model);

        EvalReport fictional = run("虚构语料(30 段 / 65 条)", C5Dataset.load(), client);
        EvalReport real = run("真实条文(2 份办法 114 片 / 62 条 query)", C5Dataset.loadReal(), client);

        assertTrue(fictional.recallAtK() > 0.0, "虚构语料向量档 0 命中, 检查 Key/模型/维度");
        assertTrue(real.recallAtK() > 0.0, "真实语料向量档 0 命中, 检查 Key/模型/维度");
    }

    /** 对一份评测集跑三档并打印; 返回 VECTOR 档报告(调用方用于断言)。 */
    private static EvalReport run(String title, C5Dataset dataset, C5EmbeddingClient client) {
        JiebaTokenizer tokenizer = new JiebaTokenizer();
        List<double[]> chunkVectors = client.embed(dataset.chunks().stream()
                .map(chunk -> chunk.section() + " " + chunk.content()).toList());
        List<String> queryTexts = dataset.queries().stream().map(EvalQuery::query).toList();
        List<double[]> queryVectorList = client.embed(queryTexts);
        Map<String, double[]> queryVectors = new HashMap<>();
        for (int i = 0; i < queryTexts.size(); i++) {
            queryVectors.put(queryTexts.get(i), queryVectorList.get(i));
        }

        Bm25Index index = Bm25Index.build(dataset.chunks().stream()
                .map(chunk -> new Bm25Document(chunk.chunkId(),
                        tokenizer.tokenize(chunk.section() + " " + chunk.content())))
                .toList(), new Bm25Params(1.5, 0.75));

        Function<String, List<Long>> vector = query ->
                byVector(dataset, chunkVectors, queryVectors.get(query), CANDIDATES);
        Function<String, List<Long>> bm25 = query -> index.search(tokenizer.tokenize(query), CANDIDATES).stream()
                .map(Bm25Index.ScoredChunk::chunkId).toList();
        Function<String, List<Long>> hybrid = query -> RrfFusion.fuse(
                Map.of("vector", vector.apply(query), "bm25", bm25.apply(query)), RRF_K, CANDIDATES).stream()
                .map(RrfFusion.FusedHit::chunkId).toList();

        EvalReport vectorReport = RecallEvaluator.evaluate("VECTOR", dataset.queries(), vector, TOP_K);
        EvalReport bm25Report = RecallEvaluator.evaluate("BM25", dataset.queries(), bm25, TOP_K);
        EvalReport hybridReport = RecallEvaluator.evaluate("HYBRID", dataset.queries(), hybrid, TOP_K);

        System.out.println("===== C5 三档对比 | " + title + " =====");
        print(vectorReport, dataset);
        print(bm25Report, dataset);
        print(hybridReport, dataset);
        assertEquals(dataset.queries().size(), vectorReport.total());
        return vectorReport;
    }

    /** 打印总体 + 分档 + 该档未命中明细。 */
    private static void print(EvalReport report, C5Dataset dataset) {
        EvalReport literal = rescore(report, dataset, false);
        EvalReport paraphrase = rescore(report, dataset, true);
        System.out.println(String.format(Locale.ROOT, "%-8s Recall@3=%.4f MRR=%.4f | 字面=%.4f 改写=%.4f",
                report.label(), report.recallAtK(), report.mrr(),
                literal.recallAtK(), paraphrase.recallAtK()));
        report.missed().forEach(miss -> System.out.println("[C5] " + report.label() + " MISS " + miss.queryId()
                + " | " + miss.query() + " | gold=" + miss.gold() + " | top=" + miss.topIds()));
    }

    private static EvalReport rescore(EvalReport report, C5Dataset dataset, boolean paraphrase) {
        return RecallEvaluator.evaluate(report.label(), subset(dataset, paraphrase),
                query -> report.outcomes().stream()
                        .filter(outcome -> outcome.query().equals(query))
                        .findFirst().map(QueryOutcome::topIds).orElse(List.of()),
                report.k());
    }

    private static List<Long> byVector(C5Dataset dataset, List<double[]> chunkVectors,
                                       double[] queryVector, int limit) {
        List<Map.Entry<Long, Double>> scored = new ArrayList<>(chunkVectors.size());
        for (int i = 0; i < chunkVectors.size(); i++) {
            scored.add(Map.entry(dataset.chunks().get(i).chunkId(),
                    C5EmbeddingClient.cosine(queryVector, chunkVectors.get(i))));
        }
        return scored.stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<EvalQuery> subset(C5Dataset dataset, boolean paraphrase) {
        return dataset.queries().stream().filter(query -> query.isParaphrase() == paraphrase).toList();
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
