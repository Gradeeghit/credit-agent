package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.application.dto.DocumentDto;
import com.credit.agent.orchestration.application.dto.PolicyDocDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.ingest.DocumentIngestService;
import com.credit.agent.rag.ingest.IngestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B0 上传入库单测: 正常入库、非 PDF、超大文件、空文件四类分支。 */
class PolicyDocAppServiceTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    private final DocumentAppService documentAppService = mock(DocumentAppService.class);
    private final DocumentIngestService ingestService = mock(DocumentIngestService.class);

    @Test
    @DisplayName("合法 PDF: 先登记 document, 再用同一个 docId 入库并回写 PARSED")
    void ingestValidPdf(@TempDir Path tempDir) {
        when(documentAppService.register(any())).thenReturn(documentView(7L));
        when(ingestService.ingest(eq(7L), eq("2024年报.pdf"), any())).thenReturn(new IngestResult("PARSED", 25, null));

        PolicyDocDto.Result result = service(tempDir).uploadAndIngest("2024年报.pdf", PDF);

        assertEquals(7L, result.documentId());
        assertEquals("PARSED", result.parseStatus());
        assertEquals(25, result.chunkCount());
        verify(documentAppService).updateParseStatus(eq(7L), any());
    }

    @Test
    @DisplayName("非 .pdf 扩展名返回 415, 不落盘不登记")
    void rejectNonPdfExtension(@TempDir Path tempDir) {
        BizException ex = assertThrows(BizException.class,
                () -> service(tempDir).uploadAndIngest("2024年报.docx", PDF));

        assertEquals(415, ex.getStatus());
        verify(documentAppService, never()).register(any());
    }

    @Test
    @DisplayName("扩展名是 .pdf 但文件头不是 PDF, 返回 415")
    void rejectFakePdfContent(@TempDir Path tempDir) {
        byte[] fake = "hello".getBytes(StandardCharsets.US_ASCII);

        BizException ex = assertThrows(BizException.class,
                () -> service(tempDir).uploadAndIngest("a.pdf", fake));

        assertEquals(415, ex.getStatus());
    }

    @Test
    @DisplayName("超过 10MB 返回 413; 空文件返回 400")
    void rejectOversizeAndEmpty(@TempDir Path tempDir) {
        byte[] oversize = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, oversize, 0, PDF.length);

        assertEquals(413, assertThrows(BizException.class,
                () -> service(tempDir).uploadAndIngest("big.pdf", oversize)).getStatus());
        assertEquals(400, assertThrows(BizException.class,
                () -> service(tempDir).uploadAndIngest("empty.pdf", new byte[0])).getStatus());
    }

    private PolicyDocAppService service(Path tempDir) {
        return new PolicyDocAppService(documentAppService, ingestService, tempDir.toString());
    }

    private static DocumentDto.View documentView(Long id) {
        return new DocumentDto.View(id, null, "POLICY_DOC", "uploads/x.pdf", 1, true, "PENDING", null, null, null);
    }
}
