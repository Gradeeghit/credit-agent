package com.credit.agent.rag.chunk;

import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.rag.parse.ParsedPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 标题层级切分单测: 章节边界、滑窗长度、跨页页码三条主线。 */
class HeadingChunkingStrategyTest {

    private final HeadingChunkingStrategy strategy = new HeadingChunkingStrategy();

    @Test
    @DisplayName("按 第X章 断章, 每块带上所属章节标题与起始页")
    void splitByChapterHeading() {
        List<ParsedPage> pages = List.of(
                new ParsedPage(1, "第一章 总则\n第一条 为规范授信行为制定本办法。", 0),
                new ParsedPage(2, "第二章 授信\n第五条 授信额度按收入倍数核定。", 30));

        List<Chunk> chunks = strategy.split(pages);

        assertEquals(2, chunks.size());
        assertEquals("第一章 总则", chunks.get(0).section());
        assertEquals(1, chunks.get(0).pageStart());
        assertEquals("第二章 授信", chunks.get(1).section());
        assertEquals(2, chunks.get(1).pageStart());
        assertTrue(chunks.get(0).text().contains("第一条"));
        assertTrue(chunks.get(1).charStart() > chunks.get(0).charStart());
    }

    @Test
    @DisplayName("超长章节滑窗: 每块长度落在 [650,800], 重叠 150 且不丢尾段")
    void slideWindowForOversizeSection() {
        String body = "授信额度按收入倍数核定。".repeat(200);
        List<Chunk> chunks = strategy.split(List.of(new ParsedPage(1, body, 0)));

        assertTrue(chunks.size() >= 2);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().length() >= 650, "块过短: " + chunk.text().length());
            assertTrue(chunk.text().length() <= 800, "块过长: " + chunk.text().length());
        }
        assertEquals(0, chunks.get(0).charStart());
        int last = chunks.get(chunks.size() - 1).charStart()
                + chunks.get(chunks.size() - 1).text().length();
        assertEquals(body.length(), last);
        assertNull(chunks.get(0).section());
    }

    @Test
    @DisplayName("跨页章节: 首块页码区间覆盖两页")
    void chunkSpansTwoPages() {
        String page1 = "第一条 " + "甲".repeat(480);
        String page2 = "乙".repeat(500);
        List<Chunk> chunks = strategy.split(List.of(
                new ParsedPage(1, page1, 0),
                new ParsedPage(2, page2, page1.length() + 1)));

        Chunk first = chunks.get(0);
        assertEquals(1, first.pageStart());
        assertEquals(2, first.pageEnd());
    }

    @Test
    @DisplayName("空输入与全空白页面不产生切片")
    void blankInputProducesNothing() {
        assertTrue(strategy.split(List.of()).isEmpty());
        assertTrue(strategy.split(List.of(new ParsedPage(1, "  \n\n ", 0))).isEmpty());
    }

    @Test
    @DisplayName("解析状态常量与 Document 表约束一致, 防止 800 字口径被悄悄改掉")
    void constantsStayAligned() {
        assertEquals(800, HeadingChunkingStrategy.MAX_CHARS);
        assertEquals(150, HeadingChunkingStrategy.OVERLAP);
        assertEquals("PARSED", Document.PARSE_PARSED);
    }
}
