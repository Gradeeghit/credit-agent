package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.application.dto.AnalysisDto;
import com.credit.agent.orchestration.application.observability.TraceContext;
import com.credit.agent.orchestration.report.GeneratedReport;
import com.credit.agent.orchestration.report.ReportGenerationService;
import com.credit.agent.orchestration.report.RuleEngineAssembler;
import com.credit.agent.orchestration.report.RuleEngineResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 一次初审分析的应用入口(F/G 主链 + H1 + H9)。
 * 业务含义: 信审员点"发起初审"时, 先算钱(规则引擎, 确定性), 再让 AI 分域分析并出报告;
 * trace_id 优先沿用请求头(MDC), 没有才生成 —— 保证 HTTP 入口到报告的 trace 是一条线。
 */
@Service
public class AnalysisAppService {

    private final RuleEngineAssembler ruleEngineAssembler;
    private final ReportGenerationService reportGenerationService;

    public AnalysisAppService(RuleEngineAssembler ruleEngineAssembler,
                              ReportGenerationService reportGenerationService) {
        this.ruleEngineAssembler = ruleEngineAssembler;
        this.reportGenerationService = reportGenerationService;
    }

    /**
     * 跑一次完整初审并落报告。
     * 业务含义: 模型不可用时依然返回报告(降级), 并把 degraded 明确告诉前端; 规则引擎结果始终都在。
     *
     * @param applicationId 申请单 ID
     * @param traceId       链路追踪 ID, 可为空(优先用请求上下文)
     * @param pendingItems  待补材料清单, 可为空
     * @return 报告 + 降级标记
     */
    public AnalysisDto.Result analyze(Long applicationId, String traceId, List<String> pendingItems) {
        String trace = (traceId == null || traceId.isBlank())
                ? TraceContext.currentOr(TraceContext.newTraceId()) : traceId;
        RuleEngineResult rules = ruleEngineAssembler.assemble(applicationId);
        GeneratedReport generated = reportGenerationService.generateWithTrace(
                applicationId, trace, rules, pendingItems);
        return new AnalysisDto.Result(generated.report(), generated.degraded(),
                generated.failureReason() == null ? null : generated.failureReason().name());
    }
}
