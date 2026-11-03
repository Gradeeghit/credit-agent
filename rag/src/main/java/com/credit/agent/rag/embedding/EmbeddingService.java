package com.credit.agent.rag.embedding;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.observability.TraceMdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务(B5)。
 * 业务含义: 把切片正文变成 1024 维稠密向量, 供 pgvector 余弦召回;
 * 百炼单批上限 20 行, 所以必须分批调用, 一次塞 20 条以上会被拒绝。
 * 维度在入口处断言: 换模型或改默认值时立刻失败, 不把错维度写进库。
 * <p>
 * 成本: embedding 也是按 token 计费的模型调用, 每次真实请求都要经 AiCallLogger 记一条
 * (kind=EMBEDDING) —— 否则制度检索 Tool 内部触发的向量化会成为成本黑洞。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final AiCallLogger aiCallLogger;
    private final String modelName;
    private final int dimensions;
    private final int batchSize;

    public EmbeddingService(EmbeddingModel embeddingModel, AiCallLogger aiCallLogger,
                            @Value("${credit-agent.ai.embedding.model:unknown}") String modelName,
                            @Value("${credit-agent.ai.embedding.dimensions:1024}") int dimensions,
                            @Value("${credit-agent.ai.embedding.batch-size:20}") int batchSize) {
        this.embeddingModel = embeddingModel;
        this.aiCallLogger = aiCallLogger;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    /** 暴露期望维度, 供上层做建表与写入的一致性断言。 */
    public int dimensions() {
        return dimensions;
    }

    /**
     * 批量向量化, 按 batchSize(默认 20) 分批。
     * 业务含义: 空输入不发起远程调用, 直接返回空集合, 避免无意义计费。
     *
     * @throws IllegalStateException 返回向量维度与配置不一致时抛出
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            vectors.addAll(embedBatch(texts.subList(from, to)));
        }
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException(
                    "向量条数与文本条数不一致: 文本 " + texts.size() + ", 向量 " + vectors.size());
        }
        return vectors;
    }

    /**
     * 单条向量化。
     * 业务含义: 检索时把 query 转向量, 这条路径每被调用一次就是一次计费调用, 同样要记账。
     */
    public float[] embedOne(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待向量化文本不能为空");
        }
        return embedBatch(List.of(text)).get(0);
    }

    /** 单批真实调用: 计时 + 记账 + 维度断言, 三者绑在一起, 避免漏记或漏断言。 */
    private List<float[]> embedBatch(List<String> batch) {
        long startNanos = System.nanoTime();
        EmbeddingResponse response = embeddingModel.embedForResponse(batch);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        recordUsage(response, latencyMs);
        if (response == null || response.getResults() == null) {
            throw new IllegalStateException("embedding 返回为空");
        }
        List<float[]> vectors = new ArrayList<>(batch.size());
        for (Embedding embedding : response.getResults()) {
            vectors.add(requireDimension(embedding.getOutput()));
        }
        return vectors;
    }

    /**
     * 记录本次 embedding 调用。
     * 业务含义: 端点未返回 usage 时按 0 记, 宁可成本偏低也不编造 token 数;
     * 记账失败只告警, 绝不影响向量化本身。
     */
    private void recordUsage(EmbeddingResponse response, long latencyMs) {
        try {
            EmbeddingResponseMetadata metadata = response == null ? null : response.getMetadata();
            Usage usage = metadata == null ? null : metadata.getUsage();
            long promptTokens = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens();
            String model = metadata == null || metadata.getModel() == null ? modelName : metadata.getModel();
            aiCallLogger.log(new AiCallRecord(TraceMdc.traceId(), model, promptTokens, 0L, latencyMs,
                    AiCallRecord.Kind.EMBEDDING));
        } catch (RuntimeException e) {
            log.warn("embedding 用量记账失败, 已忽略: {}", e.getMessage());
        }
    }

    private float[] requireDimension(float[] vector) {
        if (vector == null) {
            throw new IllegalStateException("embedding 返回空向量");
        }
        if (vector.length != dimensions) {
            throw new IllegalStateException(
                    "向量维度不匹配: 期望 " + dimensions + ", 实际 " + vector.length
                            + "; 请检查 embedding 模型与 document_chunk.embedding 定义是否一致");
        }
        return vector;
    }
}
