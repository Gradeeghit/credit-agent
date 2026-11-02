package com.credit.agent.rag.eval;

/**
 * 评测语料中的一段切片(C5)。
 * 业务含义: Recall 必须有"标准答案"才有意义, 所以语料是固定的、标注是人工的;
 * 字段与 document_chunk 对齐(docName/page/section/content), 保证评测口径与线上切片一致。
 *
 * @param chunkId 切片主键(评测内唯一, 与 query 的 gold 对应)
 * @param docName 文档名, 用于引用渲染核对
 * @param page    页码
 * @param section 章节标题
 * @param content 正文
 */
public record EvalChunk(long chunkId, String docName, int page, String section, String content) {
}
