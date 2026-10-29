package com.credit.agent.rag.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文本层解析器(B1)。
 * 业务含义: 只读文本层, 不猜图像内容; 抽取不出文字就诚实标 UNSUPPORTED 转人工,
 * 加密/损坏标 FAILED 并给出可读原因, 让信审员判断是否重传。整个过程不调用任何 LLM。
 */
@Component
public class PdfPageTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfPageTextExtractor.class);

    /**
     * 逐页抽取 PDF 文本, 并记录每页在整篇文本中的字符偏移。
     * 业务含义: 页码决定报告引用 [文件名 p.N] 是否可信, 偏移决定切片能否回链原文。
     *
     * @param pdfBytes 已上传的 PDF 原始字节, 不做落盘
     * @return 三种结局之一: PARSED / UNSUPPORTED / FAILED, 不抛异常给上层
     */
    public PdfParseResult extract(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return PdfParseResult.failed("文件内容为空");
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extractPages(document);
        } catch (IOException e) {
            log.warn("PDF 解析失败: {}", e.getMessage());
            return PdfParseResult.failed(shortMessage(e));
        } catch (RuntimeException e) {
            log.warn("PDF 解析异常", e);
            return PdfParseResult.failed("无法识别的 PDF 结构");
        }
    }

    private PdfParseResult extractPages(PDDocument document) throws IOException {
        int total = document.getNumberOfPages();
        if (total <= 0) {
            return PdfParseResult.failed("PDF 不含任何页面");
        }
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        List<ParsedPage> pages = new ArrayList<>(total);
        StringBuilder fullText = new StringBuilder();
        for (int pageNo = 1; pageNo <= total; pageNo++) {
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            String text = normalize(stripper.getText(document));
            pages.add(new ParsedPage(pageNo, text, fullText.length()));
            fullText.append(text).append('\n');
        }
        if (fullText.toString().isBlank()) {
            return PdfParseResult.unsupported();
        }
        return PdfParseResult.parsed(pages, fullText.toString());
    }

    /** 统一换行符并去掉行尾空白, 保证切分与字符偏移在 Windows/Linux 环境一致。 */
    private static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String replaced = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\f", "");
        StringBuilder sb = new StringBuilder(replaced.length());
        for (String line : replaced.split("\n", -1)) {
            String trimmedEnd = line.replaceAll("[ \t\u00A0]+$", "");
            if (trimmedEnd.isEmpty() && sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                continue;
            }
            sb.append(trimmedEnd).append('\n');
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString().strip();
    }

    /** 把底层异常压缩成一句可落 parse_error 的短原因。 */
    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("\\s+", " ");
        return singleLine.length() > 200 ? singleLine.substring(0, 200) : singleLine;
    }
}
