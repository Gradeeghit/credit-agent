package com.credit.agent.rag.retrieval.bm25;

import java.util.List;

/**
 * 参与 BM25 打分的一篇切片(C1)。
 * 业务含义: 只保留打分需要的两项 —— 主键与词序列; 正文与页码在检索结果装配阶段再取,
 * 这样索引对象足够轻, 便于整体缓存。
 *
 * @param chunkId document_chunk 主键
 * @param tokens  已分词的词序列(可含重复词, 词频由它统计)
 */
public record Bm25Document(long chunkId, List<String> tokens) {

    /** 文档长度 = 词数, 用于长度归一化。 */
    public int length() {
        return tokens == null ? 0 : tokens.size();
    }
}
