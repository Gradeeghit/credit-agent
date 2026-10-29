package com.credit.agent.rag.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 元数据 JSON 转义单测: 引用回链字段被引号/换行污染时必须仍然合法。 */
class MetadataJsonTest {

    @Test
    @DisplayName("普通切片元数据包含 docId/docName/section/charStart 四个回链字段")
    void chunkMetadataContainsLinkFields() {
        String json = MetadataJson.chunkMetadata(12L, "2024年报.pdf", "第一章 总则", 350);

        assertEquals("{\"docId\":12,\"docName\":\"2024年报.pdf\",\"section\":\"第一章 总则\",\"charStart\":350}", json);
    }

    @Test
    @DisplayName("章节为空时输出 null 字面量, 不写成空字符串")
    void nullSectionBecomesJsonNull() {
        assertTrue(MetadataJson.chunkMetadata(1L, "a.pdf", null, 0).contains("\"section\":null"));
    }

    @Test
    @DisplayName("文件名含引号、反斜杠、换行时必须转义")
    void escapeSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", MetadataJson.quote("a\"b\\c\nd"));
        assertEquals("null", MetadataJson.quote(null));
        assertEquals("\"\\u0001\"", MetadataJson.quote("\u0001"));
    }
}
