package com.credit.agent.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 调 OpenAI 兼容的 embeddings 接口(C5 向量档评分用)。
 * 业务含义: 评测语料在内存里, 向量也就不必入库 —— 直接对 30 段语料 + 65 条 query 求向量,
 * 与线上走同一模型、同一维度即可, 又不会污染 document_chunk。
 * 注意: base-url 不能带 /v1(项目已知坑), 这里统一剥掉后再拼 /v1/embeddings。
 */
final class C5EmbeddingClient {

    private static final int BATCH_SIZE = 20;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpoint;
    private final String apiKey;
    private final String model;

    C5EmbeddingClient(String baseUrl, String apiKey, String model) {
        this.endpoint = stripV1(baseUrl) + "/v1/embeddings";
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 批量求向量; 顺序与入参一致。 */
    List<double[]> embed(List<String> texts) {
        List<double[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            vectors.addAll(embedBatch(texts.subList(from, Math.min(from + BATCH_SIZE, texts.size()))));
        }
        return vectors;
    }

    private List<double[]> embedBatch(List<String> texts) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("encoding_format", "float");
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding 调用失败: HTTP " + response.statusCode()
                        + " " + abbreviate(response.body()));
            }
            return parse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("embedding 调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding 调用被中断", e);
        }
    }

    private List<double[]> parse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        List<double[]> vectors = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            JsonNode embedding = item.path("embedding");
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        if (vectors.isEmpty()) {
            throw new IllegalStateException("embedding 返回为空: " + abbreviate(json));
        }
        return vectors;
    }

    /** 余弦相似度; 维度不一致直接报错, 避免静默算错。 */
    static double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("向量维度不一致: " + left.length + " vs " + right.length);
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String stripV1(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : trimmed;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
