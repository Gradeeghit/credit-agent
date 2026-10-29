package com.credit.agent.orchestration.application.dto;

import java.time.LocalDateTime;

/**
 * 初审报告对外契约(G6)。
 * 业务含义: 报告一经生成即锁定, 只提供只读查询; 内容为六节结构 JSON 原文。
 */
public final class ReportDto {

    private ReportDto() {
    }

    /** 报告视图, contentJson 为原始 JSON 字符串。 */
    public record View(
            Long id,
            Long applicationId,
            String traceId,
            Integer version,
            String conclusion,
            String contentJson,
            Boolean requiresHumanReview,
            Boolean locked,
            LocalDateTime createdAt) {
    }
}
