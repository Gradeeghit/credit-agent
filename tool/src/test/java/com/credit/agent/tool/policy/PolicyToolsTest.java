package com.credit.agent.tool.policy;

import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.VectorRetriever;
import com.credit.agent.tool.contract.ToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E3 searchPolicy 单测: 命中带引用、超时降级、参数校验。 */
class PolicyToolsTest {

    private final VectorRetriever vectorRetriever = mock(VectorRetriever.class);

    @Test
    @DisplayName("命中制度条款: 每条都带 [文档名 p.起-止] 引用与 chunkId 回链")
    void hitCarriesCitation() {
        when(vectorRetriever.searchByDocType(anyString(), anyInt(), anyString())).thenReturn(List.of(
                new RetrievedChunk(11L, 6L, "2024年报.pdf", "第一章 总则", "正文", 12, 13, 0.87)));

        ToolResponse<List<PolicyHit>> response =
                new PolicyTools(vectorRetriever, 3000).searchPolicy("逾期几次算不良", null);

        assertTrue(response.isOk());
        assertEquals(1, response.data().size());
        assertEquals("[2024年报.pdf p.12-13]", response.data().get(0).citation());
        assertEquals(11L, response.data().get(0).chunkId());
    }

    @Test
    @DisplayName("检索超时: DEGRADED + TOOL_TIMEOUT + 空结果, 不抛异常")
    void timeoutDegrades() {
        when(vectorRetriever.searchByDocType(anyString(), anyInt(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return List.of();
        });

        ToolResponse<List<PolicyHit>> response =
                new PolicyTools(vectorRetriever, 50).searchPolicy("额度上限", 3);

        assertEquals("DEGRADED", response.status());
        assertEquals("TOOL_TIMEOUT", response.code());
        assertTrue(response.data().isEmpty());
    }

    @Test
    @DisplayName("空关键词: PARAM_INVALID, 不发起检索(避免无意义向量化计费)")
    void blankKeywordRejected() {
        PolicyTools tools = new PolicyTools(vectorRetriever, 3000);

        assertEquals("PARAM_INVALID", tools.searchPolicy("  ", 5).code());
        assertEquals("PARAM_INVALID", tools.searchPolicy(null, 5).code());
        verify(vectorRetriever, never()).searchByDocType(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("召回条数越界: PARAM_INVALID, 防止一次拉全库")
    void limitOutOfRangeRejected() {
        PolicyTools tools = new PolicyTools(vectorRetriever, 3000);

        assertEquals("PARAM_INVALID", tools.searchPolicy("额度", 0).code());
        assertEquals("PARAM_INVALID", tools.searchPolicy("额度", 11).code());
    }

    @Test
    @DisplayName("检索内部异常: 包装成 RETRIEVAL_FAILED, 堆栈不进 LLM")
    void retrievalFailureIsWrapped() {
        when(vectorRetriever.searchByDocType(anyString(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("db down"));

        ToolResponse<List<PolicyHit>> response =
                new PolicyTools(vectorRetriever, 3000).searchPolicy("额度", 5);

        assertEquals("ERROR", response.status());
        assertEquals("RETRIEVAL_FAILED", response.code());
        assertNull(response.data());
    }
}
