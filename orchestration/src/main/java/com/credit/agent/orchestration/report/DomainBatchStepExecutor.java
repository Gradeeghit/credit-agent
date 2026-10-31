package com.credit.agent.orchestration.report;

import com.credit.agent.agent.domain.DomainAgent;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.orchestration.runtime.AgentStepExecutor;
import com.credit.agent.orchestration.runtime.StepResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 把"按域逐个分析"包装成 Agent 执行的步骤(AgentRuntime 的 domain 批次执行器)。
 * 业务含义: 报告生成必须被编排层的步数与预算管住 —— 每分析一个域算一步,
 * 域数用完即 done; 预算耗尽时由 AgentRuntime 提前掐断, 这里保留已完成的部分结论作为降级报告素材。
 * 本类不调用 LLM 自身: 真正的模型调用在 {@link DomainAgent} 里(经 LlmGateway 与 AiCallLogger)。
 */
public class DomainBatchStepExecutor implements AgentStepExecutor {

    private final DomainAgent domainAgent;
    private final String traceId;
    private final List<DomainBrief> briefs;
    private final List<DomainAnswer> answers = new ArrayList<>();

    public DomainBatchStepExecutor(DomainAgent domainAgent, String traceId, List<DomainBrief> briefs) {
        this.domainAgent = domainAgent;
        this.traceId = traceId;
        this.briefs = briefs == null ? List.of() : List.copyOf(briefs);
    }

    /**
     * 分析第 stepIndex 个域。
     * 业务含义: 一个域一步, 步数与 token 都如实上报, 让 F2 的双终止对"出报告"这条链路真实生效;
     * 最后一个域执行完即 done, 不需要额外一轮空转。
     *
     * @param stepIndex 步序号, 从 1 开始, 与 approval_step.step_index 对齐
     * @return 本步 token 与观察摘要(域 + 结论)
     */
    @Override
    public StepResult act(int stepIndex) {
        DomainBrief brief = briefs.get(stepIndex - 1);
        DomainAnswer answer = domainAgent.analyze(traceId, brief);
        answers.add(answer);
        boolean lastStep = stepIndex >= briefs.size();
        return new StepResult(lastStep, answer.tokensUsed(), brief.role() + ": " + answer.conclusion());
    }

    /** 已完成的域结论; 被预算掐断时只包含前几个域, 正好用作降级报告。 */
    public List<DomainAnswer> answers() {
        return List.copyOf(answers);
    }

    /** 计划分析的域数量, 供日志与自检。 */
    public int plannedSteps() {
        return briefs.size();
    }
}
