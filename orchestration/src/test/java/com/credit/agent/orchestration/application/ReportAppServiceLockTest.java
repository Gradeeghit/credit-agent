package com.credit.agent.orchestration.application;

import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G3 锁定守卫单测: 已锁定报告内容不可变(409), 未锁定才允许写。 */
class ReportAppServiceLockTest {

    private final ApprovalReportService reportService = mock(ApprovalReportService.class);
    private final ReportAppService service = new ReportAppService(reportService);

    @Test
    @DisplayName("已锁定报告: 修改内容返回 409, 且不落库")
    void lockedReportRejectsUpdate() {
        when(reportService.getById(1L)).thenReturn(report(true, 3));

        BizException ex = assertThrows(BizException.class,
                () -> service.updateContent(1L, "{\"conclusion\":\"PASS_SUGGEST\"}"));

        assertEquals(409, ex.getStatus());
        assertEquals("REPORT_LOCKED", ex.getCode());
        verify(reportService, never()).updateById(any());
    }

    @Test
    @DisplayName("未锁定报告: 允许写入")
    void unlockedReportAllowsUpdate() {
        when(reportService.getById(2L)).thenReturn(report(false, 1));

        service.updateContent(2L, "{\"conclusion\":\"CONFLICT\"}");

        verify(reportService).updateById(any());
    }

    @Test
    @DisplayName("内容为空 400, 报告不存在 404")
    void emptyContentAndMissingReport() {
        assertEquals(400, assertThrows(BizException.class,
                () -> service.updateContent(1L, " ")).getStatus());

        when(reportService.getById(3L)).thenReturn(null);
        assertEquals(404, assertThrows(BizException.class,
                () -> service.updateContent(3L, "{}")).getStatus());
    }

    private static ApprovalReport report(boolean locked, int version) {
        ApprovalReport report = new ApprovalReport();
        report.setId(1L);
        report.setVersion(version);
        report.setLocked(locked);
        report.setConclusion("PASS_SUGGEST");
        return report;
    }
}
