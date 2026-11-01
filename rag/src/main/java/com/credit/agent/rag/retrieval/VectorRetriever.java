package com.credit.agent.rag.retrieval;

import com.credit.agent.rag.embedding.EmbeddingService;
import com.credit.agent.rag.embedding.VectorFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * pgvector 向量检索(C2)。
 * 业务含义: 用问题的向量在建好的切片库里做余弦最近邻召回, 是混合检索的第一路;
 * 硬约束: 必须带 is_current = true, 否则会召回被重传替代的作废材料(PRD §3.4 明确禁止)。
 */
@Service
public class VectorRetriever {

    /** 公共列与过滤条件; metadata 里取 docName/section 供引用渲染, 避免再 join 一次 document。 */
    private static final String SELECT_COLUMNS = """
            SELECT id, doc_id, content, page_start, page_end,
                   metadata->>'docName' AS doc_name,
                   metadata->>'section' AS section,
                   1 - (embedding <=> CAST(? AS vector)) AS score
            FROM document_chunk
            WHERE is_current = true
              AND embedding IS NOT NULL""";

    /** 全库检索; 参数顺序: 向量, 向量, limit */
    static final String SQL_BY_QUERY = SELECT_COLUMNS
            + "\nORDER BY embedding <=> CAST(? AS vector)\nLIMIT ?";

    /** 限定单份文档检索; 参数顺序: 向量, docId, 向量, limit */
    static final String SQL_BY_QUERY_AND_DOC = SELECT_COLUMNS
            + "\n  AND doc_id = ?\nORDER BY embedding <=> CAST(? AS vector)\nLIMIT ?";

    /**
     * 按材料类型检索(制度检索必须用它)。
     * 业务含义: 制度检索只能命中 policy 类文档 —— 全库检索会把客户身份证/流水切片当成"制度条款"召回,
     * 既是引用错误, 也是越界读取; 因此必须 join document 按 doc_type 收口。
     */
    private static final String SELECT_COLUMNS_OF_DOC_TYPE = """
            SELECT c.id, c.doc_id, c.content, c.page_start, c.page_end,
                   c.metadata->>'docName' AS doc_name,
                   c.metadata->>'section' AS section,
                   1 - (c.embedding <=> CAST(? AS vector)) AS score
            FROM document_chunk c
            JOIN document d ON d.id = c.doc_id
            WHERE c.is_current = true
              AND d.is_current = true
              AND c.embedding IS NOT NULL""";

    /** 按类型检索; 参数顺序: 向量, doc_type, 向量, limit */
    static final String SQL_BY_QUERY_AND_DOC_TYPE = SELECT_COLUMNS_OF_DOC_TYPE
            + "\n  AND d.doc_type = ?\nORDER BY c.embedding <=> CAST(? AS vector)\nLIMIT ?";

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public VectorRetriever(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    /**
     * 按自然语言问题召回最相似的切片。
     * 业务含义: 返回结果已按相似度降序, 上层可直接作为 BM25 融合的一路输入;
     * 只召回已向量化且当前有效的切片, 未完成向量化的切片不会出现在结果里。
     *
     * @param query 中文问题或关键词
     * @param limit 召回条数, 由上层做区间校验
     * @param docId 限定文档, 为 null 表示全库检索
     */
    public List<RetrievedChunk> search(String query, int limit, Long docId) {
        String literal = VectorFormatter.toLiteral(embeddingService.embedOne(query));
        if (docId == null) {
            return jdbcTemplate.query(SQL_BY_QUERY, this::toChunk, literal, literal, limit);
        }
        return jdbcTemplate.query(SQL_BY_QUERY_AND_DOC, this::toChunk, literal, docId, literal, limit);
    }

    /**
     * 按材料类型召回(制度检索的唯一正确入口)。
     * 业务含义: 只召回指定 doc_type 的当前有效切片, 避免把客户材料当制度依据;
     * 文档维度同样要求 is_current, 重传后的旧版本不会命中。
     *
     * @param query   中文问题或关键词
     * @param limit   召回条数
     * @param docType document.doc_type, 如 POLICY_DOC
     * @throws IllegalArgumentException docType 为空
     */
    public List<RetrievedChunk> searchByDocType(String query, int limit, String docType) {
        if (docType == null || docType.isBlank()) {
            throw new IllegalArgumentException("docType 不能为空: 不限定类型的检索会跨材料域串数据");
        }
        String literal = VectorFormatter.toLiteral(embeddingService.embedOne(query));
        return jdbcTemplate.query(SQL_BY_QUERY_AND_DOC_TYPE, this::toChunk, literal, docType, literal, limit);
    }

    private RetrievedChunk toChunk(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RetrievedChunk(
                rs.getLong("id"),
                rs.getLong("doc_id"),
                rs.getString("doc_name"),
                rs.getString("section"),
                rs.getString("content"),
                rs.getObject("page_start", Integer.class),
                rs.getObject("page_end", Integer.class),
                rs.getDouble("score"));
    }
}
