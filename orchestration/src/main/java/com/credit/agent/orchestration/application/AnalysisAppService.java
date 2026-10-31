package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.report.ReportGenerationService;
import com.credit.agent.orchestration.report.RuleEngineAssembler;
import com.credit.agent.orchestration.report.RuleEngineResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 一次初审分析的应用入口(F/G 主链)。
 * 业务含义: 信审员点"发起初审"时, 系统先算钱(规则引擎, 确定性), 再让 AI 分域分析并出报告;
 * 顺序不能反 —— 报告里的额度/月供必须是先算好的事实, 而不是让模型解释一个还没算的数字。
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
     * 业务含义: traceId 缺省时由服务端生成, 保证报告永远能按 trace 回放(H1 未落地前的兜底)。
     *
     * @param applicationId 申请单 ID
     * @param traceId       链路追踪 ID, 可为空
     * @param pendingItems  待补材料清单, 可为空
     * @return 新生成(且已锁定)的报告视图
     */
    public ReportDto.View analyze(Long applicationId, String traceId, List<String> pendingItems) {
        String trace = (traceId == null || traceId.isBlank()) ? "trace-" + UUID.randomUUID() : traceId;
        RuleEngineResult rules = ruleEngineAssembler.assemble(applicationId);
        return reportGenerationService.generate(applicationId, trace, rules, pendingItems);
    }
}
