package com.credit.agent.orchestration.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行循环(F1 状态机 + F2 双终止)。
 * 业务含义: 审批分析不允许无限追问 —— 步数上限防止"绕圈", token 预算防止"烧钱";
 * 两个条件是**独立**的终止原因, 谁先命中就按谁记录 failure_reason, 绝不合并成一个"超限"。
 * 本类不调用 LLM、不写库: 单步动作由 {@link AgentStepExecutor} 注入, 保证状态机可单测。
 */
@Component
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    /** 默认步数上限(PRD F2) */
    public static final int DEFAULT_MAX_STEPS = 8;

    /** 默认 token 预算(PRD F2) */
    public static final long DEFAULT_TOKEN_BUDGET = 20_000L;

    private final int maxSteps;
    private final long tokenBudget;

    public AgentRuntime(@Value("${credit-agent.agent.max-steps:8}") int maxSteps,
                        @Value("${credit-agent.agent.token-budget:20000}") long tokenBudget) {
        this.maxSteps = maxSteps;
        this.tokenBudget = tokenBudget;
    }

    /**
     * 跑一次完整执行循环, 直到收敛或被终止条件打断。
     * 业务含义: 步数到顶时"第 N+1 步不发起"(避免多花一次钱), token 超出时立即停(已经花掉的保留在部分结果里)。
     *
     * @param executor 单步执行器
     * @return 执行结局, 终止时 observations 即部分结果
     */
    public AgentRunResult run(AgentStepExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor 不能为空");
        }
        AgentStateMachine machine = new AgentStateMachine();
        List<String> observations = new ArrayList<>();
        int steps = 0;
        long tokens = 0;
        while (true) {
            machine.moveTo(AgentPhase.ACT);
            if (steps >= maxSteps) {
                machine.moveTo(AgentPhase.FINISHED);
                log.warn("Agent 步数到顶: steps={}, maxSteps={}", steps, maxSteps);
                return AgentRunResult.failed(FailureReason.MAX_STEPS, steps, tokens, observations);
            }
            StepResult result = executor.act(steps + 1);
            steps++;
            tokens += Math.max(0, result.tokensUsed());
            observations.add(result.observation());
            machine.moveTo(AgentPhase.OBSERVE);
            machine.moveTo(AgentPhase.REFLECT);
            if (tokens > tokenBudget) {
                machine.moveTo(AgentPhase.FINISHED);
                log.warn("Agent token 超预算: tokens={}, budget={}", tokens, tokenBudget);
                return AgentRunResult.failed(FailureReason.BUDGET_EXCEEDED, steps, tokens, observations);
            }
            if (result.done()) {
                machine.moveTo(AgentPhase.FINISHED);
                return AgentRunResult.completed(steps, tokens, observations);
            }
            machine.moveTo(AgentPhase.PLAN);
        }
    }

    /** 步数上限, 供上层展示与自检。 */
    public int maxSteps() {
        return maxSteps;
    }

    /** token 预算, 供上层展示与自检。 */
    public long tokenBudget() {
        return tokenBudget;
    }
}
