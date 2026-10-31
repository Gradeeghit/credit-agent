package com.credit.agent.orchestration.report;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.CitationRef;
import com.credit.agent.agent.domain.DomainAgent;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.agent.domain.DomainVerdict;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.runtime.DomainBriefAssembler;
import com.credit.agent.orchestration.runtime.IncomeConsistencyDetector;
import com.credit.agent.orchestration.runtime.SupervisorAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G3 报告生成与锁定单测: 版本递增、立即锁定、状态推进、终态拒绝。 */
class ReportGenerationServiceTest {

    private final DomainBriefAssembler assembler = mock(DomainBriefAssembler.class);
    private final DomainAgent domainAgent = mock(DomainAgent.class);
    private final ApprovalReportService reportService = mock(ApprovalReportService.class);
    private final LoanApplicationService applicationService = mock(LoanApplicationService.class);
    private final SupervisorAgent supervisor =
            new SupervisorAgent(new IncomeConsistencyDetector(new BigDecimal("0.30")));
    private final ReportGenerationService service = new ReportGenerationService(assembler, domainAgent,
            supervisor, new ReportContentBuilder(), reportService, applicationService);

    @Test
    @DisplayName("首次生成: version=1, locked=true, 结论与人工复核标记来自汇聚, 申请单推进 REPORT_READY")
    void firstGenerationIsLocked() {
        givenApplication(LoanApplication.STATUS_PROCESSING);
        givenAnswers(incomeAnswer("30000"));
        when(reportService.getOne(any(Wrapper.class), eq(false))).thenReturn(null);

        ReportDto.View view = service.generate(9L, "trace-1", rules(), List.of());

        ArgumentCaptor<ApprovalReport> captor = ArgumentCaptor.forClass(ApprovalReport.class);
        verify(reportService).save(captor.capture());
        ApprovalReport saved = captor.getValue();
        assertEquals(1, saved.getVersion());
        assertTrue(saved.getLocked());
        assertEquals("trace-1", saved.getTraceId());
        assertEquals("PASS_SUGGEST", saved.getConclusion());
        assertTrue(saved.getRequiresHumanReview());
        assertTrue(saved.getContentJson().contains("\"conclusion\""));
        assertTrue(saved.getContentJson().contains("chunkId"));
        assertEquals("PASS_SUGGEST", view.conclusion());

        ArgumentCaptor<LoanApplication> patch = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applicationService).updateById(patch.capture());
        assertEquals(LoanApplication.STATUS_REPORT_READY, patch.getValue().getStatus());
    }

    @Test
    @DisplayName("重跑: 生成 version=2, 旧版本不被修改(只读保留)")
    void rerunCreatesNextVersion() {
        givenApplication(LoanApplication.STATUS_NEED_MORE_INFO);
        givenAnswers(incomeAnswer("30000"));
        ApprovalReport existing = new ApprovalReport();
        existing.setId(5L);
        existing.setVersion(1);
        existing.setLocked(true);
        when(reportService.getOne(any(Wrapper.class), eq(false))).thenReturn(existing);

        ReportDto.View view = service.generate(9L, "trace-2", rules(), List.of());

        assertEquals(2, view.version());
        verify(reportService, never()).updateById(any());
    }

    @Test
    @DisplayName("终态申请单不允许重新生成报告: 409")
    void terminalApplicationRejected() {
        givenApplication(LoanApplication.STATUS_APPROVED);

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(9L, "trace-1", rules(), List.of()));
        assertEquals(409, ex.getStatus());
        verify(reportService, never()).save(any());
    }

    @Test
    @DisplayName("申请单不存在: 404; traceId 为空: 直接拒绝")
    void missingApplicationAndTrace() {
        when(applicationService.getById(9L)).thenReturn(null);
        assertEquals(404, assertThrows(BizException.class,
                () -> service.generate(9L, "trace-1", rules(), List.of())).getStatus());

        givenApplication(LoanApplication.STATUS_SUBMITTED);
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(9L, " ", rules(), List.of()));
    }

    @Test
    @DisplayName("冲突场景: 结论 CONFLICT 且报告风险点非空")
    void conflictConclusionPropagates() {
        givenApplication(LoanApplication.STATUS_PROCESSING);
        when(assembler.assemble(9L)).thenReturn(List.of(
                DomainBrief.forMaterial(9L, List.of()), DomainBrief.forStatement(9L, List.of())));
        when(domainAgent.analyze(anyString(), any())).thenReturn(
                new DomainAnswer(AgentRole.MATERIAL, "收入证明 3 万", List.of(new CitationRef(1L, "收入.pdf", 1, 1)),
                        100, DomainVerdict.NORMAL,
                        Map.of(DomainAnswer.FACT_INCOME_MONTHLY, new BigDecimal("30000"))),
                new DomainAnswer(AgentRole.STATEMENT, "流水 2 万", List.of(new CitationRef(2L, "流水.pdf", 1, 1)),
                        100, DomainVerdict.NORMAL,
                        Map.of(DomainAnswer.FACT_STATEMENT_MONTHLY_AVG, new BigDecimal("20000"))));
        when(reportService.getOne(any(Wrapper.class), eq(false))).thenReturn(null);

        ReportDto.View view = service.generate(9L, "trace-1", rules(), List.of());

        assertEquals("CONFLICT", view.conclusion());
        assertTrue(view.contentJson().contains("INCOME_INCONSISTENT"));
        verify(domainAgent, atLeastOnce()).analyze(any(), any());
    }

    private void givenApplication(String status) {
        LoanApplication application = new LoanApplication();
        application.setId(9L);
        application.setStatus(status);
        when(applicationService.getById(9L)).thenReturn(application);
    }

    private void givenAnswers(DomainAnswer answer) {
        when(assembler.assemble(9L)).thenReturn(List.of(DomainBrief.forMaterial(9L, List.of())));
        when(domainAgent.analyze(anyString(), any())).thenReturn(answer);
    }

    private static DomainAnswer incomeAnswer(String income) {
        return new DomainAnswer(AgentRole.MATERIAL, "材料正常", List.of(new CitationRef(1L, "收入.pdf", 1, 1)),
                100, DomainVerdict.NORMAL,
                Map.of(DomainAnswer.FACT_INCOME_MONTHLY, new BigDecimal(income)));
    }

    private static RuleEngineResult rules() {
        return new RuleEngineResult(new BigDecimal("240000.00"), new BigDecimal("200000.00"),
                "PASS", new BigDecimal("0.00"), new BigDecimal("200000.00"),
                new BigDecimal("1887.12"), new BigDecimal("1000.00"), List.of("未超限"));
    }
}
