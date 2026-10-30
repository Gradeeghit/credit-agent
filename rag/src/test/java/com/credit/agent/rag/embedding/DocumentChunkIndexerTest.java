package com.credit.agent.rag.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 向量化编排单测: 开关关闭跳过、正常写入、embedding 失败降级。 */
class DocumentChunkIndexerTest {

    private final ChunkVectorStore store = mock(ChunkVectorStore.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);

    @Test
    @DisplayName("开关关闭时不查库也不调用模型")
    void shouldSkipWhenDisabled() {
        DocumentChunkIndexer indexer = new DocumentChunkIndexer(store, embeddingService, false);

        DocumentChunkIndexer.IndexOutcome outcome = indexer.index(7L);

        assertEquals(0, outcome.attempted());
        assertEquals(0, outcome.updated());
        assertTrue(outcome.reason().contains("未启用"));
        verifyNoInteractions(store, embeddingService);
    }

    @Test
    @DisplayName("正常路径: 待处理切片逐条回填向量")
    void shouldWriteVectors() {
        when(store.findPending(7L)).thenReturn(List.of(
                new ChunkVectorStore.PendingChunk(1L, "第一章 总则"),
                new ChunkVectorStore.PendingChunk(2L, "第二章 利率")));
        when(embeddingService.embedAll(anyList())).thenReturn(List.of(new float[1024], new float[1024]));
        when(store.updateVector(anyLong(), any(float[].class))).thenReturn(1);
        DocumentChunkIndexer indexer = new DocumentChunkIndexer(store, embeddingService, true);

        DocumentChunkIndexer.IndexOutcome outcome = indexer.index(7L);

        assertEquals(2, outcome.attempted());
        assertEquals(2, outcome.updated());
        assertNull(outcome.reason());
        ArgumentCaptor<float[]> captor = ArgumentCaptor.forClass(float[].class);
        verify(store, times(2)).updateVector(anyLong(), captor.capture());
        captor.getAllValues().forEach(vector -> assertEquals(1024, vector.length));
    }

    @Test
    @DisplayName("embedding 失败不抛异常, 返回降级原因且不写库")
    void shouldDegradeOnEmbeddingFailure() {
        when(store.findPending(7L)).thenReturn(List.of(new ChunkVectorStore.PendingChunk(1L, "正文")));
        when(embeddingService.embedAll(anyList())).thenThrow(new IllegalStateException("LLM_TIMEOUT"));
        DocumentChunkIndexer indexer = new DocumentChunkIndexer(store, embeddingService, true);

        DocumentChunkIndexer.IndexOutcome outcome = indexer.index(7L);

        assertEquals(0, outcome.updated());
        assertTrue(outcome.reason().startsWith("EMBEDDING_FAILED"));
        verify(store, never()).updateVector(anyLong(), any(float[].class));
    }
}
