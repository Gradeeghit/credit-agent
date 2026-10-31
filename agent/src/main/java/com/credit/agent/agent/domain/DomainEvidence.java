package com.credit.agent.agent.domain;

/**
 * 一条域内证据(切片级)。
 * 业务含义: 子 Agent 看到的每句话都必须能回链到某份文档的某一页,
 * 因此证据带着 chunkId 与页码走, 而不是把整份 PDF 文本倒进 prompt。
 *
 * @param chunkId   document_chunk 主键, 引用回链凭据
 * @param docId     来源文档版本记录
 * @param docName   文档名, 用于渲染 [文件名 p.N]
 * @param pageStart 起始页, 可为空
 * @param pageEnd   结束页, 可为空
 * @param content   证据正文
 */
public record DomainEvidence(
        long chunkId,
        long docId,
        String docName,
        Integer pageStart,
        Integer pageEnd,
        String content) {
}
