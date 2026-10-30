package com.credit.agent.rag.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 向量字面量单测: 正常拼接、空向量拒绝、非有限值拒绝。 */
class VectorFormatterTest {

    @Test
    @DisplayName("转成 pgvector 可解析的 [..] 字面量, 保留正负号")
    void shouldFormatVector() {
        assertEquals("[1.0,-0.5,0.25]", VectorFormatter.toLiteral(new float[]{1.0f, -0.5f, 0.25f}));
    }

    @Test
    @DisplayName("空向量与 null 直接拒绝, 避免写出非法的 [] 字面量")
    void rejectEmptyVector() {
        assertEquals("向量不能为空",
                assertThrows(IllegalArgumentException.class, () -> VectorFormatter.toLiteral(new float[0])).getMessage());
        assertThrows(IllegalArgumentException.class, () -> VectorFormatter.toLiteral(null));
    }

    @Test
    @DisplayName("NaN/Infinity 在写入前拦截, 不让 PG 报错")
    void rejectNonFiniteValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VectorFormatter.toLiteral(new float[]{0.1f, Float.NaN}));

        assertTrue(ex.getMessage().contains("第 1 维"));
    }
}
