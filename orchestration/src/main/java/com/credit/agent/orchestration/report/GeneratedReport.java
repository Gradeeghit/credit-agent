package com.credit.agent.orchestration.report;

import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.runtime.FailureReason;

/**
 * 一次报告生成的结果(H9)。
 * 业务含义: 报告本身 + "是否降级"; 降级时 failureReason 非空, 上层据此在响应里标 degraded=true,
 * 前端与审批人一眼能看出"这份报告的 AI 分析不完整"。
 *
 * @param report        已锁定报告视图
 * @param failureReason 终止原因; 为 null 表示分析完整
 */
public record GeneratedReport(ReportDto.View report, FailureReason failureReason) {

    /** 是否降级产出。 */
    public boolean degraded() {
        return failureReason != null;
    }
}
