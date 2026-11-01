package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.application.dto.RetrievalDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.VectorRetriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 检索应用服务单测: 参数校验、引用渲染、结果映射。 */
class RetrievalAppServiceTest {

    private final VectorRetriever vectorRetriever = mock(VectorRetriever.class);
    private final RetrievalAppService service = new RetrievalAppService(vectorRetriever);

    @Test
    @DisplayName("命中项自动带 [文档名 p.起-止] 引用, 跨页与单页格式都正确")
    void shouldRenderCitation() {
        when(vectorRetriever.search(eq("逾期几次算不良"), anyInt(), isNull())).thenReturn(List.of(
                new RetrievedChunk(11L, 6L, "2024年报.pdf", "第一章 总则", "正文", 12, 13, 0.87)));

        List<RetrievalDto.Hit> hits = service.search(
                new RetrievalDto.SearchRequest("逾期几次算不良", null, null, null));

        assertEquals(1, hits.size());
        assertEquals("[2024年报.pdf p.12-13]", hits.get(0).citation());
        assertEquals(0.87, hits.get(0).score());
    }

    @Test
    @DisplayName("单页与缺页码的引用格式")
    void citationFormats() {
        assertEquals("[制度.pdf p.5]", RetrievalAppService.citation("制度.pdf", 5, 5));
        assertEquals("[制度.pdf p.5-7]", RetrievalAppService.citation("制度.pdf", 5, 7));
        assertEquals("[制度.pdf]", RetrievalAppService.citation("制度.pdf", null, null));
        assertEquals("[未知文档 p.1]", RetrievalAppService.citation(" ", 1, 1));
    }

    @Test
    @DisplayName("空问题不发起检索, 直接 400")
    void blankQueryShouldFail() {
        BizException ex = assertThrows(BizException.class,
                () -> service.search(new RetrievalDto.SearchRequest("  ", 5, null, null)));

        assertEquals(400, ex.getStatus());
        verify(vectorRetriever, never()).search(anyString(), anyInt(), isNull());
    }

    @Test
    @DisplayName("召回条数越界返回 400, 防止一次拉全库")
    void limitOutOfRangeShouldFail() {
        assertEquals(400, assertThrows(BizException.class,
                () -> service.search(new RetrievalDto.SearchRequest("额度", 100, null, null))).getStatus());
        assertEquals(400, assertThrows(BizException.class,
                () -> service.search(new RetrievalDto.SearchRequest("额度", 0, null, null))).getStatus());
    }
}
