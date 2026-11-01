package com.credit.agent.rag.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索 SQL 单测(C2 验收点②)。
 * 业务含义: 召回 SQL 一旦漏掉 is_current 过滤, 就会把重传前的作废材料当依据,
 * 这类错误在真库上极难发现, 所以用断言把约束钉死在 SQL 常量上。
 */
class VectorRetrieverTest {

    @Test
    @DisplayName("两条检索 SQL 都必须带 is_current = true 与余弦算子")
    void sqlMustFilterCurrentChunks() {
        for (String sql : new String[]{VectorRetriever.SQL_BY_QUERY, VectorRetriever.SQL_BY_QUERY_AND_DOC}) {
            assertTrue(sql.contains("is_current = true"), "缺少 is_current 过滤: " + sql);
            assertTrue(sql.contains("embedding IS NOT NULL"), "未向量化的切片必须排除: " + sql);
            assertTrue(sql.contains("<=> CAST(? AS vector)"), "必须用余弦距离: " + sql);
            assertTrue(sql.contains("ORDER BY embedding"), "必须按距离升序: " + sql);
        }
    }

    @Test
    @DisplayName("只有限定文档的 SQL 才带 doc_id 过滤")
    void onlyScopedSqlFiltersByDoc() {
        assertFalse(VectorRetriever.SQL_BY_QUERY.contains("doc_id = ?"));
        assertTrue(VectorRetriever.SQL_BY_QUERY_AND_DOC.contains("doc_id = ?"));
    }

    @Test
    @DisplayName("相似度取值 = 1 - 余弦距离, 便于上层直接按分数排序理解")
    void scoreEqualsOneMinusDistance() {
        assertTrue(VectorRetriever.SQL_BY_QUERY.contains("1 - (embedding <=> CAST(? AS vector)) AS score"));
    }

    @Test
    @DisplayName("按类型检索的 SQL: 必须 join document 且同时校验两侧 is_current 与 doc_type")
    void docTypeSqlIsScoped() {
        String sql = VectorRetriever.SQL_BY_QUERY_AND_DOC_TYPE;

        assertTrue(sql.contains("JOIN document d"), "必须 join document 才能按 doc_type 过滤");
        assertTrue(sql.contains("c.is_current = true"), "切片侧必须过滤已作废");
        assertTrue(sql.contains("d.is_current = true"), "文档侧也必须过滤已作废版本");
        assertTrue(sql.contains("d.doc_type = ?"), "必须按材料类型收口, 否则会召回客户材料");
        assertTrue(sql.contains("<=> CAST(? AS vector)"), "必须用余弦距离");
    }
}
