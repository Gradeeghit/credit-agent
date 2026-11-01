package com.credit.agent.orchestration.application.dto;

/**
 * 一次初审分析的响应契约(H9)。
 * 业务含义: 除了报告本身, 必须显式告诉调用方"这次分析是否降级" ——
 * degraded=true 时前端要醒目提示"AI 分析不完整", 不能让人误以为是完整结论。
 */
public final class AnalysisDto {

    private AnalysisDto() {
    }

    /**
     * 分析结果。
     *
     * @param report        已锁定的报告
     * @param degraded      是否降级产出(模型不可用 / 超预算 / 超步数)
     * @param failureReason 降级原因枚举名, 未降级时为 null
     */
    public record Result(ReportDto.View report, boolean degraded, String failureReason) {
    }
}
