package com.credit.agent.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档切片向量化编排(B5)。
 * 业务含义: 切片入库后把正文批量变成向量并回填 embedding 列;
 * 向量化失败不能影响切片入库本身 —— 检索会退化为纯 BM25, 而不是整份文档不可用(E3 降级)。
 */
@Service
public class DocumentChunkIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkIndexer.class);

    private final ChunkVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final boolean enabled;

    public DocumentChunkIndexer(ChunkVectorStore vectorStore, EmbeddingService embeddingService,
                                @Value("${credit-agent.ai.embedding.enabled:false}") boolean enabled) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.enabled = enabled;
    }

    /**
     * 对某个文档版本下所有未向量化的当前切片做向量化。
     * 业务含义: 幂等 —— 已写入向量的切片不会被重复处理; 关闭开关时直接跳过, 不发起远程调用。
     *
     * @param docId document 表主键(同一份文档的不同版本是不同的 docId)
     */
    public IndexOutcome index(Long docId) {
        if (!enabled) {
            return new IndexOutcome(0, 0, "未启用(credit-agent.ai.embedding.enabled=false)");
        }
        List<ChunkVectorStore.PendingChunk> pending = vectorStore.findPending(docId);
        if (pending.isEmpty()) {
            return new IndexOutcome(0, 0, null);
        }
        try {
            List<String> texts = pending.stream().map(ChunkVectorStore.PendingChunk::content).toList();
            List<float[]> vectors = embeddingService.embedAll(texts);
            int updated = 0;
            for (int i = 0; i < pending.size(); i++) {
                updated += vectorStore.updateVector(pending.get(i).id(), vectors.get(i));
            }
            log.info("切片向量化完成: docId={}, 待处理={}, 已写入={}", docId, pending.size(), updated);
            return new IndexOutcome(pending.size(), updated, null);
        } catch (RuntimeException e) {
            log.warn("切片向量化失败, 检索降级为纯 BM25: docId={}, reason={}", docId, e.getMessage());
            return new IndexOutcome(pending.size(), 0, "EMBEDDING_FAILED: " + e.getMessage());
        }
    }

    /**
     * 向量化结果。
     *
     * @param attempted 待处理切片数
     * @param updated   实际写入向量数
     * @param reason    跳过或失败原因, 成功为 null
     */
    public record IndexOutcome(int attempted, int updated, String reason) {
    }
}
