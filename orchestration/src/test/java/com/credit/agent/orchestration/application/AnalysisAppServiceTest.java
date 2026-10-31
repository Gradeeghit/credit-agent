package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.report.ReportGenerationService;
import com.credit.agent.orchestration.report.RuleEngineAssembler;
import com.credit.agent.orchestration.report.RuleEngineResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 分析入口单测: 先算钱再出报告, traceId 缺省时服务端兜底。 */
class AnalysisAppServiceTest {

    private final RuleEngineAssembler ruleEngineAssembler = mock(RuleEngineAssembler.class);
    private final ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
    private final AnalysisAppService service =
            new AnalysisAppService(ruleEngineAssembler, reportGenerationService);

    @Test
    @DisplayName("traceId 为空时生成 trace- 前缀, 并把规则引擎结果透传给报告生成")
    void generatesTraceIdWhenMissing() {
        when(ruleEngineAssembler.assemble(9L)).thenReturn(rules());

        service.analyze(9L, "  ", null);

        verify(reportGenerationService).generate(eq(9L), startsWith("trace-"), any(), any());
    }

    @Test
    @DisplayName("传入 traceId 与待补清单时原样透传")
    void passesThroughTraceAndPendingItems() {
        when(ruleEngineAssembler.assemble(9L)).thenReturn(rules());

        service.analyze(9L, "trace-abc", List.of("补交近 6 个月流水"));

        verify(reportGenerationService).generate(9L, "trace-abc", rules(), List.of("补交近 6 个月流水"));
    }

    private static RuleEngineResult rules() {
        return new RuleEngineResult(new BigDecimal("240000.00"), new BigDecimal("200000.00"),
                "PASS", new BigDecimal("0.00"), new BigDecimal("200000.00"),
                new BigDecimal("16666.67"), new BigDecimal("1000.00"), List.of("未超限"));
    }
}
