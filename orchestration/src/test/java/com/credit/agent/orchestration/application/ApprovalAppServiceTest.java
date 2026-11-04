package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.ApprovalDecisionDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.application.observability.AuditRecorder;
import com.credit.agent.orchestration.application.observability.TraceRecorder;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G4 签批单测: 三种动作、幂等、版本 409、状态前置、意见必填、越权与并发。 */
class ApprovalAppServiceTest {

    private static final CurrentUser APPROVER = new CurrentUser(9L, UserRole.APPROVER);
    private static final String TRACE = "t-decision-1";

    private final LoanApplicationService applicationService = mock(LoanApplicationService.class);
    private final ApprovalReportService reportService = mock(ApprovalReportService.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final TraceRecorder traceRecorder = mock(TraceRecorder.class);
    private final ApprovalAppService service = new ApprovalAppService(
            applicationService, reportService, auditRecorder, traceRecorder);

    @Test
    @DisplayName("通过: REPORT_READY → APPROVED, 写 1 条审计与 1 条步骤")
    void approveMovesApplicationToApproved() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(1, false);
        when(applicationService.update(any(Wrapper.class))).thenReturn(true);

        ApprovalDecisionDto.Result result = service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), APPROVER, TRACE);

        assertEquals(LoanApplication.STATUS_APPROVED, result.status());
        assertFalse(result.idempotent());
        verify(auditRecorder).recordDecision(any(), eq(TRACE), eq(1L), eq(1), eq(false), any(), any());
        verify(traceRecorder).step(eq(TRACE), eq(1), eq("APPROVER"), isNull(), any(), any(), eq(0L), isNull());
    }

    @Test
    @DisplayName("驳回: REPORT_READY → REJECTED")
    void rejectMovesApplicationToRejected() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(2, false);
        when(applicationService.update(any(Wrapper.class))).thenReturn(true);

        ApprovalDecisionDto.Result result = service.decide(1L,
                new ApprovalDecisionDto.Request("REJECT", 2, "流水与收入证明偏差过大"), APPROVER, TRACE);

        assertEquals(LoanApplication.STATUS_REJECTED, result.status());
        assertEquals("REJECT", result.action());
    }

    @Test
    @DisplayName("要求补材料: REPORT_READY → NEED_MORE_INFO(非终态)")
    void needMoreInfoKeepsFlowOpen() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(1, true);
        when(applicationService.update(any(Wrapper.class))).thenReturn(true);

        ApprovalDecisionDto.Result result = service.decide(1L,
                new ApprovalDecisionDto.Request("NEED_MORE_INFO", 1, "请补充最近六个月流水"), APPROVER, TRACE);

        assertEquals(LoanApplication.STATUS_NEED_MORE_INFO, result.status());
    }

    @Test
    @DisplayName("重复通过: 返回首次结果, 不更新状态、不写审计、不写步骤")
    void repeatedApproveIsIdempotent() {
        givenApplication(LoanApplication.STATUS_APPROVED);
        givenReport(1, false);

        ApprovalDecisionDto.Result result = service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, "再次点击"), APPROVER, TRACE);

        assertTrue(result.idempotent());
        assertEquals(LoanApplication.STATUS_APPROVED, result.status());
        verify(applicationService, never()).update(any(Wrapper.class));
        verify(auditRecorder, never()).recordDecision(any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(traceRecorder, never()).step(any(), anyInt(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("版本不匹配: 409, 且不改状态")
    void versionMismatchIsConflict() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(2, false);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), APPROVER, TRACE));

        assertEquals(409, ex.getStatus());
        assertEquals("REPORT_VERSION_MISMATCH", ex.getCode());
        verify(applicationService, never()).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("状态不是 REPORT_READY: 409(分析中不能签批)")
    void statusNotReportReadyIsConflict() {
        givenApplication(LoanApplication.STATUS_PROCESSING);
        givenReport(1, false);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), APPROVER, TRACE));

        assertEquals(409, ex.getStatus());
        assertEquals("APPLICATION_STATUS_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("已通过的单子再驳回: 409(终态不可逆)")
    void differentActionOnTerminalIsConflict() {
        givenApplication(LoanApplication.STATUS_APPROVED);
        givenReport(1, false);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("REJECT", 1, "改主意了"), APPROVER, TRACE));

        assertEquals(409, ex.getStatus());
    }

    @Test
    @DisplayName("需人工复核但通过意见不足 10 字: 400, 不落状态")
    void reviewReportRequiresMeaningfulComment() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(1, true);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, "同意"), APPROVER, TRACE));

        assertEquals(400, ex.getStatus());
        assertEquals("COMMENT_TOO_SHORT", ex.getCode());
        verify(applicationService, never()).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("并发: 状态已被别人改掉(update 影响 0 行) → 409, 不写审计")
    void lostRaceIsConflict() {
        givenApplication(LoanApplication.STATUS_REPORT_READY);
        givenReport(1, false);
        when(applicationService.update(any(Wrapper.class))).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), APPROVER, TRACE));

        assertEquals(409, ex.getStatus());
        verify(auditRecorder, never()).recordDecision(any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("信审员签批: 403")
    void checkerCannotDecide() {
        CurrentUser checker = new CurrentUser(8L, UserRole.CHECKER);

        BizException ex = assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), checker, TRACE));

        assertEquals(403, ex.getStatus());
    }

    @Test
    @DisplayName("缺版本号: 400; 报告不存在: 404")
    void missingVersionAndMissingReport() {
        assertEquals(400, assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", null, null), APPROVER, TRACE)).getStatus());

        givenApplication(LoanApplication.STATUS_REPORT_READY);
        when(reportService.getOne(any(), anyBoolean())).thenReturn(null);
        assertEquals(404, assertThrows(BizException.class, () -> service.decide(1L,
                new ApprovalDecisionDto.Request("APPROVE", 1, null), APPROVER, TRACE)).getStatus());
    }

    private void givenApplication(String status) {
        LoanApplication application = new LoanApplication();
        application.setId(1L);
        application.setStatus(status);
        when(applicationService.getById(1L)).thenReturn(application);
    }

    private void givenReport(int version, boolean requiresHumanReview) {
        ApprovalReport report = new ApprovalReport();
        report.setId(100L);
        report.setApplicationId(1L);
        report.setVersion(version);
        report.setRequiresHumanReview(requiresHumanReview);
        when(reportService.getOne(any(), anyBoolean())).thenReturn(report);
    }
}
