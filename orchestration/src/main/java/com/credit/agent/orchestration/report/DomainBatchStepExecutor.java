package com.credit.agent.orchestration.report;

import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.DomainAgent;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.orchestration.runtime.AgentStepExecutor;
import com.credit.agent.orchestration.runtime.StepResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 把"按域逐个分析"包装成 Agent 执行的步骤(AgentRuntime 的 domain 批次执行器)。
 * 业务含义: 报告生成必须被编排层的步数与预算管住 —— 每分析一个域算一步, 域数用完即 done;
 * 同时每步耗时与结论通过 {@link StepListener} 上报, 供 H1 落 approval_step(可回放)。
 * 本类不调用 LLM 自身: 真正的模型调用在 {@link DomainAgent} 里。
 */
public class DomainBatchStepExecutor implements AgentStepExecutor {

    private final DomainAgent domainAgent;
    private final String traceId;
    private final List<DomainBrief> briefs;
    private final StepListener listener;
    private final List<DomainAnswer> answers = new ArrayList<>();

    public DomainBatchStepExecutor(DomainAgent domainAgent, String traceId, List<DomainBrief> briefs) {
        this(domainAgent, traceId, briefs, null);
    }

    public DomainBatchStepExecutor(DomainAgent domainAgent, String traceId, List<DomainBrief> briefs,
                                   StepListener listener) {
        this.domainAgent = domainAgent;
        this.traceId = traceId;
        this.briefs = briefs == null ? List.of() : List.copyOf(briefs);
        this.listener = listener;
    }

    /**
     * 分析第 stepIndex 个域。
     * 业务含义: 一个域一步, 步数与 token 都如实上报, 让 F2 的双终止对"出报告"这条链路真实生效;
     * 耗时在此测量(模型调用 + 上下文组装), 最后一个域执行完即 done。
     *
     * @param stepIndex 步序号, 从 1 开始, 与 approval_step.step_index 对齐
     * @return 本步 token 与观察摘要(域 + 结论)
     */
    @Override
    public StepResult act(int stepIndex) {
        DomainBrief brief = briefs.get(stepIndex - 1);
        long startNanos = System.nanoTime();
        DomainAnswer answer = domainAgent.analyze(traceId, brief);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        answers.add(answer);
        if (listener != null) {
            listener.onStep(new StepEvent(stepIndex, brief.role(), answer.conclusion(),
                    latencyMs, answer.tokensUsed()));
        }
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

    /** 单步执行事件(H1 的 Agent/Tool 段落点)。 */
    public record StepEvent(int stepIndex, AgentRole role, String observation, long latencyMs, long tokens) {
    }

    /** 步骤监听器: 由编排层注入, 用于写 approval_step 或打日志。 */
    @FunctionalInterface
    public interface StepListener {
        void onStep(StepEvent event);
    }
}
