package com.credit.agent.agent.domain;

import com.credit.agent.rag.retrieval.Citation;

/**
 * 结构化引用(G1 的硬要求: 每条引用必须能回链到存在的 chunk_id)。
 * 业务含义: 报告里写 [2024年报.pdf p.12-13] 只是给人看的;
 * 机器要能凭 chunkId 回到 document_chunk 复核原文, 所以引用必须带 ID 而不是纯字符串。
 *
 * @param chunkId   document_chunk 主键, 必须为正数
 * @param docName   文档名
 * @param pageStart 起始页, 可为空
 * @param pageEnd   结束页, 可为空
 */
public record CitationRef(long chunkId, String docName, Integer pageStart, Integer pageEnd) {

    public CitationRef {
        if (chunkId <= 0) {
            throw new IllegalArgumentException("chunkId 必须为正数, 否则引用无法回链: " + chunkId);
        }
    }

    /** 渲染成人读引用, 如 [2024年报.pdf p.12-13]。 */
    public String text() {
        return Citation.render(docName, pageStart, pageEnd);
    }
}
