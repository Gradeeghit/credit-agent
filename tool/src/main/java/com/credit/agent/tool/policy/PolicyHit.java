package com.credit.agent.tool.policy;

/**
 * 制度检索命中项(E3 的返回体)。
 * 业务含义: LLM 引用制度条款时必须能说清"哪份文件、第几页、哪一章",
 * 因此 citation 与 chunkId 一并返回, 报告落库后可回链 document_chunk 复核。
 *
 * @param chunkId   document_chunk 主键, 引用回链的唯一凭据
 * @param docId     所属文档版本记录
 * @param docName   文档名
 * @param section   章节标题, 可能为空
 * @param content   切片正文
 * @param pageStart 起始页码
 * @param pageEnd   结束页码
 * @param score     余弦相似度, 越接近 1 越相关
 * @param citation  已渲染好的引用串, 如 [2024年报.pdf p.12-13]
 */
public record PolicyHit(
        long chunkId,
        long docId,
        String docName,
        String section,
        String content,
        Integer pageStart,
        Integer pageEnd,
        double score,
        String citation) {
}
