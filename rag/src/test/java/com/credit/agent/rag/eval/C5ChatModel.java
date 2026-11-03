package com.credit.agent.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评测专用的 ChatModel 实现(C4 验收用)。
 * 业务含义: C4 的重排效果必须用**真实的** {@code LlmReranker} 跑一遍才有意义, 不能只测解析器;
 * 但评测在纯 JUnit 环境里, 没有 Spring 容器, 所以这里用 JDK HttpClient 直连
 * {@code /v1/chat/completions}, 造一个最小 ChatModel 塞给 {@code LlmReranker}。
 * <p>
 * 与 {@link C5EmbeddingClient} 同一套约定: base-url 不能带 {@code /v1}(项目已知坑),
 * 这里统一剥掉后再拼。
 */
final class C5ChatModel implements ChatModel {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpoint;
    private final String apiKey;
    private final String model;

    C5ChatModel(String baseUrl, String apiKey, String model) {
        this.endpoint = stripV1(baseUrl) + "/v1/chat/completions";
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String userText = prompt.getInstructions().stream()
                .map(Message::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        // 与生产一致: 温度 0, 保证同一批候选每次重排出同一顺序(评测可复现)
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", userText);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("chat 调用失败: HTTP " + response.statusCode()
                        + " " + abbreviate(response.body()));
            }
            return parse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("chat 调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("chat 调用被中断", e);
        }
    }

    private ChatResponse parse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return OpenAiChatOptions.builder().build();
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
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
