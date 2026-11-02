package com.credit.agent.rag.retrieval.bm25;

import com.credit.agent.rag.retrieval.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25 稀疏检索(C1)。
 * 业务含义: 用词面匹配补向量的短板 —— 产品编码(P1)、条款号(第 3.2 条)、"LPR+100bp" 这类精确 token
 * 在 embedding 里几乎是噪声, 但对 BM25 是强信号。制度检索两路都要。
 * <p>
 * 与向量检索的差异: 返回的 score 是 BM25 分(无上界), 与余弦相似度不同量纲, **融合时只能用排名**(RRF)。
 * 性能说明: 当前每次查询都对全量当前切片重新分词建索引, 适合几千条量级的教学数据;
 * 数据量上来后应把 tokens 落表或加 docId+更新时间 维度的缓存。
 */
@Service
public class Bm25Retriever {

    private static final String SELECT_COLUMNS = """
            SELECT c.id, c.doc_id, c.content, c.page_start, c.page_end,
                   c.metadata->>'docName' AS doc_name,
                   c.metadata->>'section' AS section
            FROM document_chunk c
            JOIN document d ON d.id = c.doc_id
            WHERE c.is_current = true
              AND d.is_current = true
              AND c.content IS NOT NULL""";

    // 过滤条件按需拼接: docType(材料类型)与 docId(单份文档)都可选, 但双侧 is_current 恒定保留。

    private final JdbcTemplate jdbcTemplate;
    private final Tokenizer tokenizer;
    private final Bm25Params params;
    private final int maxChunks;

    public Bm25Retriever(JdbcTemplate jdbcTemplate, Tokenizer tokenizer,
                         @Value("${credit-agent.retrieval.bm25.k1:1.5}") double k1,
                         @Value("${credit-agent.retrieval.bm25.b:0.75}") double b,
                         @Value("${credit-agent.retrieval.bm25.max-chunks:5000}") int maxChunks) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizer = tokenizer;
        this.params = new Bm25Params(k1, b);
        this.maxChunks = maxChunks;
    }

    /**
     * 按词面相关性召回切片。
     * 业务含义: 传入 docType 时只在该类型文档内检索(制度域必须传 POLICY_DOC), 避免跨材料域串数据。
     *
     * @param query   问题或关键词
     * @param limit   返回条数
     * @param docType 限定材料类型, 为空表示全库
     */
    public List<RetrievedChunk> search(String query, int limit, String docType) {
        return search(query, limit, docType, null);
    }

    /**
     * 按词面相关性召回切片, 可同时限定材料类型与单份文档。
     * 业务含义: docType 用于制度域收口(只命中 POLICY_DOC), docId 用于单份文档排查;
     * 两者都为空时是全库检索。
     */
    public List<RetrievedChunk> search(String query, int limit, String docType, Long docId) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        List<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<Row> rows = loadChunks(docType, docId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Row> byId = new HashMap<>(rows.size() * 2);
        List<Bm25Document> documents = new ArrayList<>(rows.size());
        for (Row row : rows) {
            byId.put(row.chunkId(), row);
            documents.add(new Bm25Document(row.chunkId(), tokenizer.tokenize(row.content())));
        }
        Bm25Index index = Bm25Index.build(documents, params);
        return index.search(queryTokens, limit).stream()
                .map(hit -> byId.get(hit.chunkId()).toRetrieved(hit.score()))
                .toList();
    }

    private List<Row> loadChunks(String docType, Long docId) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS);
        List<Object> args = new ArrayList<>(3);
        if (docType != null && !docType.isBlank()) {
            sql.append("\n  AND d.doc_type = ?");
            args.add(docType);
        }
        if (docId != null) {
            sql.append("\n  AND c.doc_id = ?");
            args.add(docId);
        }
        sql.append("\nORDER BY c.id\nLIMIT ?");
        args.add(maxChunks);
        return jdbcTemplate.query(sql.toString(), this::toRow, args.toArray());
    }

    private Row toRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("doc_id"), rs.getString("content"),
                rs.getObject("page_start", Integer.class), rs.getObject("page_end", Integer.class),
                rs.getString("doc_name"), rs.getString("section"));
    }

    /** 库里的切片行: 建索引只需要正文, 其余字段用于结果装配。 */
    private record Row(long chunkId, long docId, String content, Integer pageStart, Integer pageEnd,
                       String docName, String section) {

        RetrievedChunk toRetrieved(double score) {
            return new RetrievedChunk(chunkId, docId, docName, section, content, pageStart, pageEnd, score);
        }
    }
}
