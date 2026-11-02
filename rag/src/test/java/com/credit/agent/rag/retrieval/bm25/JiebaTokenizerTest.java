package com.credit.agent.rag.retrieval.bm25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分词器单测: 中文切词、空白输入、大小写归一与标点过滤。 */
class JiebaTokenizerTest {

    private final JiebaTokenizer tokenizer = new JiebaTokenizer();

    @Test
    @DisplayName("中文句子被切成多个词, 且不产生空白 token")
    void shouldCutChineseSentence() {
        List<String> tokens = tokenizer.tokenize("逾期几次算不良");

        assertTrue(tokens.size() > 1, "实际: " + tokens);
        assertFalse(tokens.contains(" "));
        assertTrue(tokens.stream().allMatch(token -> !token.isBlank()));
    }

    @Test
    @DisplayName("空与空白输入返回空列表, 不抛异常")
    void blankShouldReturnEmpty() {
        assertEquals(List.of(), tokenizer.tokenize(null));
        assertEquals(List.of(), tokenizer.tokenize("   "));
    }

    @Test
    @DisplayName("英文缩写统一小写, 纯标点被过滤")
    void shouldLowercaseAndDropPunctuation() {
        List<String> tokens = tokenizer.tokenize("LPR 加点，!!! 100bp");

        assertTrue(tokens.contains("lpr"), "实际: " + tokens);
        assertFalse(tokens.contains("，"));
        assertFalse(tokens.contains("!"));
    }
}
