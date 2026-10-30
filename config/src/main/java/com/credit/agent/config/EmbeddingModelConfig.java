package com.credit.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding 模型装配(PRD §1.5.1-D1)。
 * 业务含义: 向量化只能用百炼 qwen3.7-text-embedding-flash, 它与 chat 模型不是同一组连接参数
 * (不同 base_url, 不同 Key), 因此必须单独构造, 不能复用 spring.ai.openai.* 全局属性。
 * 标 {@code @Primary} 是为了压过 starter 按 spring.ai.openai.* 自动装配出来的那个 EmbeddingModel,
 * 避免容器里出现两个同类型 Bean 导致注入歧义。
 */
@Configuration
public class EmbeddingModelConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelConfig.class);

    /**
     * 构造独立的 Embedding 模型客户端。
     * 业务含义: dimensions 显式传 1024 并与 document_chunk.embedding 的 vector(1024) 对齐,
     * 换模型时会立刻维度不匹配而不是静默写入错数据。
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${credit-agent.ai.embedding.api-key}") String apiKey,
            @Value("${credit-agent.ai.embedding.base-url}") String baseUrl,
            @Value("${credit-agent.ai.embedding.model}") String model,
            @Value("${credit-agent.ai.embedding.dimensions:1024}") int dimensions) {

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .apiKey(apiKey)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .dimensions(dimensions)
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    /**
     * 去掉 base-url 末尾多余的斜杠与 /v1。
     * 业务含义: Spring AI 内部固定拼 /v1/embeddings, 而百炼等兼容端点文档给的 base_url 已经带 /v1,
     * 两者叠加会变成 /v1/v1/embeddings 并返回 404(实测), 这里统一收敛, 换机器/换环境不会再被绊一次。
     */
    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
            log.warn("embedding base-url 不应包含 /v1(Spring AI 会自行拼接), 已自动修正为: {}", normalized);
        }
        return normalized;
    }
}
