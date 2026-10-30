package com.credit.agent.orchestration.application;

import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import com.credit.agent.infra.persistence.service.DocumentService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.DocumentDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.embedding.DocumentChunkIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A4 重传单测: 版本递增、旧版本失效、旧切片失效, 以及路径缺失与文档不存在两类异常分支。 */
class DocumentAppServiceTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final LoanApplicationService applicationService = mock(LoanApplicationService.class);
    private final DocumentChunkService chunkService = mock(DocumentChunkService.class);
    private final DocumentChunkIndexer chunkIndexer = mock(DocumentChunkIndexer.class);
    private final DocumentAppService service =
            new DocumentAppService(documentService, applicationService, chunkService, chunkIndexer);

    @Test
    @DisplayName("重传: 新版本 version+1 且为当前有效, 旧版本与其切片同时失效")
    void resubmitShouldRetireOldVersionAndChunks() {
        when(documentService.getById(1L)).thenReturn(policyDoc(1L, 1));
        when(documentService.getById(2L)).thenReturn(policyDoc(2L, 2));
        doAnswer(inv -> {
            inv.getArgument(0, Document.class).setId(2L);
            return true;
        }).when(documentService).save(any(Document.class));

        DocumentDto.View view = service.resubmit(1L, new DocumentDto.ResubmitRequest("uploads/new.pdf", null));

        assertEquals(2L, view.id());
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documentService).save(saved.capture());
        assertEquals(2, saved.getValue().getVersion());
        assertTrue(saved.getValue().getIsCurrent());
        assertEquals(Document.PARSE_PENDING, saved.getValue().getParseStatus());

        ArgumentCaptor<Document> patched = ArgumentCaptor.forClass(Document.class);
        verify(documentService).updateById(patched.capture());
        assertEquals(1L, patched.getValue().getId());
        assertFalse(patched.getValue().getIsCurrent());
        verify(chunkService).update(any());
    }

    @Test
    @DisplayName("重传时文件路径为空返回 400, 不产生新版本也不动切片")
    void resubmitWithoutFilePathShouldFail() {
        when(documentService.getById(1L)).thenReturn(policyDoc(1L, 1));

        BizException ex = assertThrows(BizException.class,
                () -> service.resubmit(1L, new DocumentDto.ResubmitRequest("  ", null)));

        assertEquals(400, ex.getStatus());
        verify(documentService, never()).save(any(Document.class));
        verify(chunkService, never()).update(any());
    }

    @Test
    @DisplayName("材料不存在返回 404, 不误伤其他版本的切片")
    void resubmitMissingDocumentShouldFail() {
        when(documentService.getById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.resubmit(9L, new DocumentDto.ResubmitRequest("uploads/a.pdf", null)));

        assertEquals(404, ex.getStatus());
        verify(chunkService, never()).update(any());
    }

    @Test
    @DisplayName("作废材料: 文档置失效的同时失效其切片")
    void retireShouldAlsoRetireChunks() {
        when(documentService.getById(1L)).thenReturn(policyDoc(1L, 1));

        service.retire(1L);

        verify(documentService).updateById(any(Document.class));
        verify(chunkService).update(any());
    }

    @Test
    @DisplayName("重新向量化: 校验文档存在后交给索引器, 幂等由索引器保证")
    void reindexShouldDelegateToIndexer() {
        when(documentService.getById(1L)).thenReturn(policyDoc(1L, 1));
        when(chunkIndexer.index(1L)).thenReturn(new DocumentChunkIndexer.IndexOutcome(3, 3, null));

        DocumentDto.ReindexResult result = service.reindex(1L);

        assertEquals(3, result.attempted());
        assertEquals(3, result.updated());
        assertNull(result.reason());
        verify(chunkIndexer).index(1L);
    }

    private static Document policyDoc(Long id, int version) {
        Document doc = new Document();
        doc.setId(id);
        doc.setDocType(Document.DOC_TYPE_POLICY_DOC);
        doc.setFilePath("uploads/v" + version + ".pdf");
        doc.setVersion(version);
        doc.setIsCurrent(true);
        doc.setParseStatus(Document.PARSE_PARSED);
        return doc;
    }
}
