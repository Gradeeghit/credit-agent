package com.credit.agent.rag.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 切片向量的读写通道(B5)。
 * 业务含义: document_chunk.embedding 是 pgvector 的 vector 类型, MyBatis-Plus 无法映射,
 * 因此这里一律用 JdbcTemplate 手写 SQL, 并且只碰 embedding 列, 不改动实体。
 * 并发/幂等: 写入条件是 embedding IS NULL 之外的 id, 重复向量化同一批不会产生重复行。
 */
@Repository
public class ChunkVectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final int dimensions;

    public ChunkVectorStore(JdbcTemplate jdbcTemplate,
                            @Value("${credit-agent.ai.embedding.dimensions:1024}") int dimensions) {
        this.jdbcTemplate = jdbcTemplate;
        this.dimensions = dimensions;
    }

    /**
     * 查询某文档下尚未向量化的当前切片。
     * 业务含义: 只取 is_current = true, 已被重传版本替代的切片不该再浪费 embedding 调用。
     */
    public List<PendingChunk> findPending(Long docId) {
        return jdbcTemplate.query("""
                        SELECT id, content FROM document_chunk
                        WHERE doc_id = ? AND is_current = true AND embedding IS NULL
                        ORDER BY id""",
                (rs, rowNum) -> new PendingChunk(rs.getLong("id"), rs.getString("content")),
                docId);
    }

    /**
     * 写入单条切片向量。
     * 业务含义: 参数用逗号分隔字符串再 CAST, 不依赖 PGobject(驱动是 runtime 作用域)。
     *
     * @return 受影响行数, 0 表示切片不存在
     */
    public int updateVector(long chunkId, float[] vector) {
        return jdbcTemplate.update(
                "UPDATE document_chunk SET embedding = CAST(? AS vector) WHERE id = ?",
                VectorFormatter.toLiteral(vector), chunkId);
    }

    /**
     * 断言切片向量维度正确(B5 验收点②)。
     * 业务含义: 用 vector_dims 让数据库自己回答"这行是不是 1024 维", 比在 Java 侧猜测可靠。
     */
    public boolean hasExpectedDimension(long chunkId) {
        Integer matched = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunk WHERE id = ? AND vector_dims(embedding) = ?",
                Integer.class, chunkId, dimensions);
        return matched != null && matched > 0;
    }

    /** 待向量化的切片: 只需要主键与正文。 */
    public record PendingChunk(long id, String content) {
    }
}
