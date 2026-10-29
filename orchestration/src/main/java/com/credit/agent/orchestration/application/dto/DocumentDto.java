package com.credit.agent.orchestration.application.dto;

import java.time.LocalDateTime;

/**
 * 材料/政策文档对外契约(A2/A3/A4)。
 * 业务含义: 只登记文件路径与元数据(二进制存储策略另行决定);
 * 业务材料必须挂在申请单下, 政策文档(POLICY_DOC)不挂申请单。
 */
public final class DocumentDto {

    private DocumentDto() {
    }

    /** 登记材料入参; metadata 为 JSON 字符串, 需是合法 JSON。 */
    public record RegisterRequest(
            Long applicationId,
            String docType,
            String filePath,
            String metadata) {
    }

    /** 重传入参: 基于现有材料生成 version+1 的新记录。 */
    public record ResubmitRequest(String filePath, String metadata) {
    }

    /** 解析状态回写入参; FAILED 时必须给出 parseError。 */
    public record ParseStatusRequest(String parseStatus, String parseError) {
    }

    /** 材料视图。 */
    public record View(
            Long id,
            Long applicationId,
            String docType,
            String filePath,
            Integer version,
            Boolean isCurrent,
            String parseStatus,
            String parseError,
            String metadata,
            LocalDateTime createdAt) {
    }
}
