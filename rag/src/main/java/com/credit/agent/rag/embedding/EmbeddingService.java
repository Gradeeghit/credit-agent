package com.credit.agent.rag.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务(B5)。
 * 业务含义: 把切片正文变成 1024 维稠密向量, 供 pgvector 余弦召回;
 * 百炼单批上限 20 行, 所以必须分批调用, 一次塞 20 条以上会被拒绝。
 * 维度在入口处断言: 换模型或改默认值时立刻失败, 不把错维度写进库。
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final int dimensions;
    private final int batchSize;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            @Value("${credit-agent.ai.embedding.dimensions:1024}") int dimensions,
                            @Value("${credit-agent.ai.embedding.batch-size:20}") int batchSize) {
        this.embeddingModel = embeddingModel;
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
            List<float[]> batch = embeddingModel.embed(texts.subList(from, to));
            batch.forEach(vector -> vectors.add(requireDimension(vector)));
        }
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException(
                    "向量条数与文本条数不一致: 文本 " + texts.size() + ", 向量 " + vectors.size());
        }
        return vectors;
    }

    /** 单条向量化, 供查询侧复用同一套维度断言。 */
    public float[] embedOne(String text) {
        return requireDimension(embeddingModel.embed(text));
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
