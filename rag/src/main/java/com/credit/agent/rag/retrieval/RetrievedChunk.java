package com.credit.agent.rag.retrieval;

/**
 * 向量检索命中的切片。
 * 业务含义: 召回结果必须能回链到原文位置, 否则报告里的引用 [文档名 p.12-13] 无法核对;
 * score 为余弦相似度(1 - 余弦距离), 越接近 1 越相似。
 *
 * @param chunkId   document_chunk 主键, 报告 JSON 里的引用桥
 * @param docId     所属 document 版本记录
 * @param docName   文档名, 从切片 metadata 取, 用于渲染引用
 * @param section   章节标题, 可能为空
 * @param content   切片正文
 * @param pageStart 起始页码, 可能为空(无页码信息的文档)
 * @param pageEnd   结束页码, 跨页时大于 pageStart
 * @param score     余弦相似度
 */
public record RetrievedChunk(
        long chunkId,
        long docId,
        String docName,
        String section,
        String content,
        Integer pageStart,
        Integer pageEnd,
        double score) {
}
