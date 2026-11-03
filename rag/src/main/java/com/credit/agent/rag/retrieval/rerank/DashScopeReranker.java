package com.credit.agent.rag.retrieval.rerank;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.observability.TraceMdc;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 基于百炼专用重排模型的重排实现(C4, 默认 provider)。
 * 业务含义: 相比"让 chat 模型输出一个排列"({@link LlmReranker}), 专用 rerank 模型直接给
 * 每条候选的相关性分数, 不需要解析 JSON、不需要容错编号, 也便宜一个量级
 * (qwen3-rerank 约 0.5 元/百万 token)。
 * <p>
 * 端点: {@code https://{host}/compatible-api/v1/reranks}(OpenAI 兼容风格, 与 chat 的
 * {@code /compatible-mode} 不同路径, 同一个 host)。
 * 响应: {@code {"results":[{"index":0,"relevance_score":0.87}, ...]}}, index 是传入 documents 的下标。
 * <p>
 * 三条硬约束与 {@link LlmReranker} 一致: 只重排不新增、失败必降级、长度 ≤ topN。
 */
@Component
@ConditionalOnProperty(name = "credit-agent.retrieval.rerank.provider",
        havingValue = "dashscope", matchIfMissing = true)
public class DashScopeReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(DashScopeReranker.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private final AiCallLogger aiCallLogger;
    private final boolean enabled;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int maxCandidates;
    private final int contentLimit;
    private final long timeoutMs;

    /**
     * @param enabled       总开关, 默认 false(未在评测集上确认收益前不默认开启)
     * @param baseUrl       重排端点基址; 留空则由 chat 的 base-url 推导(host 相同, 路径换成 compatible-api)
     * @param apiKey        重排 Key; 留空则复用 chat 的 Key
     * @param model         重排模型, 默认 qwen3-rerank(专用 rerank 模型, 不能填 chat 模型名)
     * @param maxCandidates 一次最多给多少条打分(PRD C4: 仅对 top20 打分)
     * @param contentLimit  单条正文进请求的截断长度
     * @param timeoutMs     单次调用超时, 超时即降级
     */
    public DashScopeReranker(AiCallLogger aiCallLogger,
                             @Value("${credit-agent.retrieval.rerank.enabled:false}") boolean enabled,
                             @Value("${credit-agent.retrieval.rerank.base-url:}") String baseUrl,
                             @Value("${spring.ai.openai.base-url:}") String chatBaseUrl,
                             @Value("${credit-agent.retrieval.rerank.api-key:}") String apiKey,
                             @Value("${spring.ai.openai.api-key:}") String chatApiKey,
                             @Value("${credit-agent.retrieval.rerank.model:qwen3-rerank}") String model,
                             @Value("${credit-agent.retrieval.rerank.max-candidates:20}") int maxCandidates,
                             @Value("${credit-agent.retrieval.rerank.content-limit:200}") int contentLimit,
                             @Value("${credit-agent.retrieval.rerank.timeout-ms:8000}") long timeoutMs) {
        this.aiCallLogger = aiCallLogger;
        this.enabled = enabled;
        this.endpoint = resolveEndpoint(baseUrl, chatBaseUrl);
        this.apiKey = firstNonBlank(apiKey, chatApiKey);
        this.model = model == null || model.isBlank() ? "qwen3-rerank" : model.trim();
        this.maxCandidates = Math.max(1, maxCandidates);
        this.contentLimit = Math.max(20, contentLimit);
        this.timeoutMs = Math.max(500, timeoutMs);
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        if (!enabled) {
            log.debug("rerank 已全局关闭, 按原顺序返回前 {} 条", topN);
            return head(candidates, topN);
        }
        if (candidates.size() <= 1) {
            return head(candidates, topN);
        }
        List<RetrievedChunk> pool = candidates.size() > maxCandidates
                ? List.copyOf(candidates.subList(0, maxCandidates))
                : List.copyOf(candidates);
        try {
            List<Integer> order = callRerankApi(query, pool);
            if (order.isEmpty()) {
                log.warn("rerank 返回空结果, 降级为原顺序: trace_id={}", TraceMdc.traceId());
                return head(pool, topN);
            }
            return head(reorder(pool, order), topN);
        } catch (RuntimeException e) {
            log.warn("rerank 调用失败, 降级为原顺序: trace_id={}, err={}", TraceMdc.traceId(), e.getMessage());
            return head(pool, topN);
        }
    }

    /** 按模型给出的分数降序重排; 模型没返回的候选按原顺序补在后面(不丢片段)。 */
    private static List<RetrievedChunk> reorder(List<RetrievedChunk> pool, List<Integer> order) {
        List<RetrievedChunk> reordered = new ArrayList<>(pool.size());
        boolean[] used = new boolean[pool.size()];
        for (int index : order) {
            if (index >= 0 && index < pool.size() && !used[index]) {
                reordered.add(pool.get(index));
                used[index] = true;
            }
        }
        for (int i = 0; i < pool.size(); i++) {
            if (!used[i]) {
                reordered.add(pool.get(i));
            }
        }
        return reordered;
    }

    /**
     * 调用一次重排接口, 返回按相关性降序的候选下标。
     * 注意: **不传 top_n** —— 要拿到全部候选的分数自己截断, 否则服务端只回前 N 条,
     * 剩余候选就"消失"了, 与"只重排不新增"的契约冲突。
     */
    private List<Integer> callRerankApi(String query, List<RetrievedChunk> pool) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("query", query == null ? "" : query);
        ArrayNode documents = body.putArray("documents");
        for (RetrievedChunk chunk : pool) {
            documents.add(abbreviate(chunk.content(), contentLimit));
        }

        long startNanos = System.nanoTime();
        HttpResponse<String> response = post(body);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;

        JsonNode root = readTree(response.body());
        recordUsage(root.path("usage").path("total_tokens").asLong(0L), latencyMs);

        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalStateException("rerank 响应缺少 results");
        }
        List<Map.Entry<Integer, Double>> scored = new ArrayList<>(results.size());
        for (JsonNode item : results) {
            scored.add(Map.entry(item.path("index").asInt(-1),
                    item.path("relevance_score").asDouble(0.0)));
        }
        scored.removeIf(entry -> entry.getKey() < 0 || entry.getKey() >= pool.size());
        scored.sort(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparingInt(Map.Entry::getKey));
        return scored.stream().map(Map.Entry::getKey).toList();
    }

    private HttpResponse<String> post(ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("rerank 调用失败: HTTP " + response.statusCode()
                        + " " + abbreviate(response.body(), 200));
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("rerank 调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rerank 调用被中断", e);
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("rerank 响应不是合法 JSON: " + abbreviate(json, 120), e);
        }
    }

    /** 重排也是一次真实计费调用, 走同一条记账口; 单价按 RERANK 单独算。 */
    private void recordUsage(long tokens, long latencyMs) {
        try {
            aiCallLogger.log(new AiCallRecord(TraceMdc.traceId(), model, tokens, 0L, latencyMs,
                    AiCallRecord.Kind.RERANK, 1));
        } catch (RuntimeException e) {
            log.warn("rerank 用量记账失败, 已忽略: {}", e.getMessage());
        }
    }

    /**
     * 推导重排端点。
     * 兼容三种配置: 只给 host / 给到 /compatible-mode(chat 基址) / 直接给 /compatible-api。
     */
    static String resolveEndpoint(String rerankBaseUrl, String chatBaseUrl) {
        String base = firstNonBlank(rerankBaseUrl, chatBaseUrl);
        if (base == null) {
            throw new IllegalArgumentException("rerank base-url 未配置, 且无法从 spring.ai.openai.base-url 推导");
        }
        String normalized = base.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("/compatible-api")) {
            return normalized + "/v1/reranks";
        }
        if (normalized.endsWith("/compatible-mode")) {
            normalized = normalized.substring(0, normalized.length() - "/compatible-mode".length());
        }
        return normalized + "/compatible-api/v1/reranks";
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private static List<RetrievedChunk> head(List<RetrievedChunk> chunks, int topN) {
        return chunks.size() > topN ? List.copyOf(chunks.subList(0, topN)) : chunks;
    }

    private static String abbreviate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
