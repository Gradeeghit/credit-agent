package com.credit.agent.rag.ingest;

/**
 * 文档入库结果。
 * 业务含义: 一次入库的三种结局要能被信审员看懂 —— 成功切出多少块、
 * 影像件转人工、还是解析失败需要重传; 状态直接回写 document.parse_status。
 *
 * @param parseStatus 取值 PARSED / UNSUPPORTED / FAILED
 * @param chunkCount  实际写入 document_chunk 的切片数
 * @param error       FAILED 时的原因, 其余为 null
 */
public record IngestResult(String parseStatus, int chunkCount, String error) {
}
