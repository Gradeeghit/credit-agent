package com.credit.agent.orchestration.application.dto;

/**
 * 政策文档入库对外契约(B0)。
 * 业务含义: 上传一份制度/年报后, 前端要能立刻知道"入库成功几块"或"为什么没入库",
 * 因此把解析状态、切片数、失败原因一起返回, 不让用户去猜。
 */
public final class PolicyDocDto {

    private PolicyDocDto() {
    }

    /**
     * 入库结果。
     *
     * @param documentId  document 表主键, 后续检索与引用都靠它
     * @param docName     原始文件名, 引用渲染用
     * @param parseStatus PARSED / UNSUPPORTED / FAILED
     * @param chunkCount  写入 document_chunk 的切片条数
     * @param error       FAILED 时的原因
     */
    public record Result(Long documentId, String docName, String parseStatus, int chunkCount, String error) {
    }
}
