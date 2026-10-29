package com.credit.agent.rag.parse;

import com.credit.agent.infra.persistence.entity.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PDF 文本层解析单测: 有文本层、无文本层、非法文件三种结局。 */
class PdfPageTextExtractorTest {

    private final PdfPageTextExtractor extractor = new PdfPageTextExtractor();

    @Test
    @DisplayName("两页文本层 PDF: 逐页抽取且页码与偏移正确")
    void extractTwoPagePdf() throws IOException {
        PdfParseResult result = extractor.extract(twoPagePdf("Chapter One General", "Chapter Two Credit"));
        System.out.println(result);

        assertEquals(Document.PARSE_PARSED, result.parseStatus());
        assertEquals(2, result.pages().size());
        assertEquals(1, result.pages().get(0).pageNo());
        assertEquals(2, result.pages().get(1).pageNo());
        assertEquals(0, result.pages().get(0).charOffset());
        assertTrue(result.pages().get(1).charOffset() > 0);
        assertTrue(result.fullText().contains("Chapter Two Credit"));
    }

    @Test
    @DisplayName("无文本层(影像件)判为 UNSUPPORTED, 不伪造成解析成功")
    void blankPdfIsUnsupported() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            PdfParseResult result = extractor.extract(save(document));
            assertEquals(Document.PARSE_UNSUPPORTED, result.parseStatus());
            assertTrue(result.pages().isEmpty());
        }
    }

    @Test
    @DisplayName("非 PDF 字节判为 FAILED 且原因非空, 不向上抛异常")
    void corruptBytesFailWithReason() {
        PdfParseResult result = extractor.extract("not a pdf".getBytes(StandardCharsets.UTF_8));

        assertEquals(Document.PARSE_FAILED, result.parseStatus());
        assertNotNull(result.error());
        assertTrue(!result.error().isBlank());
    }

    @Test
    @DisplayName("空字节判为 FAILED")
    void emptyBytesFail() {
        assertEquals(Document.PARSE_FAILED, extractor.extract(new byte[0]).parseStatus());
        assertEquals(Document.PARSE_FAILED, extractor.extract(null).parseStatus());
    }

    private static byte[] twoPagePdf(String first, String second) throws IOException {
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, first);
            addTextPage(document, second);
            return save(document);
        }
    }

    private static void addTextPage(PDDocument document, String text) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(50, 700);
            stream.showText(text);
            stream.endText();
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
