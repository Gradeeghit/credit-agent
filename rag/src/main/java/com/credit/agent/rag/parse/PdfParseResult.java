package com.credit.agent.rag.parse;

import com.credit.agent.infra.persistence.entity.Document;

import java.util.List;

/**
 * PDF 解析结果。
 * 业务含义: 解析状态直接对应 document.parse_status 的三种结局 ——
 * PARSED(有文本层) / UNSUPPORTED(影像件, 诚实标注不假装能读) / FAILED(损坏或加密, 必须带原因给信审员)。
 *
 * @param parseStatus 取值只能是 PARSED / UNSUPPORTED / FAILED, 复用 Document 上的常量避免字符串漂移
 * @param pages       逐页文本, 按页码升序; 非 PARSED 时为空列表
 * @param fullText    全部页面文本按序拼接, 与 pages 的 charOffset 严格对应
 * @param error       FAILED 时的失败原因; 其余状态为 null
 */
public record PdfParseResult(String parseStatus, List<ParsedPage> pages, String fullText, String error) {

    /** 解析成功: 文本层可读。 */
    public static PdfParseResult parsed(List<ParsedPage> pages, String fullText) {
        return new PdfParseResult(Document.PARSE_PARSED, List.copyOf(pages), fullText, null);
    }

    /** 无文本层(影像件/纯图片): 转人工, 不算失败。 */
    public static PdfParseResult unsupported() {
        return new PdfParseResult(Document.PARSE_UNSUPPORTED, List.of(), "", null);
    }

    /** 解析失败: 加密、损坏、非 PDF 等, 原因必须非空。 */
    public static PdfParseResult failed(String error) {
        return new PdfParseResult(Document.PARSE_FAILED, List.of(), "", error);
    }

    /** 是否解析成功; 只有成功才允许进入切分。 */
    public boolean isParsed() {
        return Document.PARSE_PARSED.equals(parseStatus);
    }
}
