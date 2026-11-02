package com.credit.agent.rag.retrieval;

import com.credit.agent.rag.retrieval.bm25.Bm25Retriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 混合检索单测: 融合排序、单路降级、双路失败、模式透传、docType 优先。 */
class HybridRetrieverTest {

    private final VectorRetriever vectorRetriever = mock(VectorRetriever.class);
    private final Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
    private final HybridRetriever retriever = new HybridRetriever(vectorRetriever, bm25Retriever, 60, 20);

    @Test
    @DisplayName("HYBRID: 两路融合后按 RRF 分排序, score 为融合分而非余弦/BM25 分")
    void hybridFusesBothSources() {
        when(vectorRetriever.search(eq("逾期"), eq(20), isNull()))
                .thenReturn(List.of(chunk(10L, 0.9), chunk(20L, 0.8)));
        when(bm25Retriever.search(eq("逾期"), eq(20), isNull(), isNull()))
                .thenReturn(List.of(chunk(20L, 5.0), chunk(30L, 3.0)));

        List<RetrievedChunk> hits = retriever.search("逾期", 5, null, null, SearchMode.HYBRID);

        // 20 双路命中: 1/62 + 1/61 = 0.032522 排第 1
        assertEquals(3, hits.size());
        assertEquals(20L, hits.get(0).chunkId());
        assertEquals(0.032522, hits.get(0).score(), 1e-6);
        assertEquals(10L, hits.get(1).chunkId());
        assertEquals(30L, hits.get(2).chunkId());
    }

    @Test
    @DisplayName("向量路失败时降级为纯 BM25 结果, 不抛异常")
    void degradesWhenVectorFails() {
        when(vectorRetriever.search(anyString(), anyInt(), isNull()))
                .thenThrow(new IllegalStateException("embedding down"));
        when(bm25Retriever.search(anyString(), anyInt(), isNull(), isNull()))
                .thenReturn(List.of(chunk(30L, 3.0)));

        List<RetrievedChunk> hits = retriever.search("逾期", 5, null, null, SearchMode.HYBRID);

        assertEquals(1, hits.size());
        assertEquals(30L, hits.get(0).chunkId());
    }

    @Test
    @DisplayName("两路都失败时返回空列表")
    void bothFailingReturnsEmpty() {
        when(vectorRetriever.search(anyString(), anyInt(), isNull()))
                .thenThrow(new IllegalStateException("embedding down"));
        when(bm25Retriever.search(anyString(), anyInt(), isNull(), isNull()))
                .thenThrow(new IllegalStateException("db down"));

        assertTrue(retriever.search("逾期", 5, null, null, SearchMode.HYBRID).isEmpty());
    }

    @Test
    @DisplayName("VECTOR 模式只走向量路, 完全不碰 BM25")
    void vectorModeSkipsBm25() {
        when(vectorRetriever.search(eq("逾期"), eq(5), isNull())).thenReturn(List.of(chunk(10L, 0.9)));

        List<RetrievedChunk> hits = retriever.search("逾期", 5, null, null, SearchMode.VECTOR);

        assertEquals(1, hits.size());
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    @DisplayName("docType 非空时向量路走 searchByDocType, 不再全库检索")
    void docTypeTakesPrecedenceOverDocId() {
        when(vectorRetriever.searchByDocType("逾期", 20, "POLICY_DOC")).thenReturn(List.of(chunk(10L, 0.9)));
        when(bm25Retriever.search("逾期", 20, "POLICY_DOC", null)).thenReturn(List.of());

        retriever.search("逾期", 5, "POLICY_DOC", 99L, SearchMode.HYBRID);

        verify(vectorRetriever).searchByDocType("逾期", 20, "POLICY_DOC");
        verify(vectorRetriever, never()).search(anyString(), anyInt(), any());
    }

    private static RetrievedChunk chunk(long chunkId, double score) {
        return new RetrievedChunk(chunkId, 1L, "制度.pdf", "第一章", "正文", 1, 1, score);
    }
}
