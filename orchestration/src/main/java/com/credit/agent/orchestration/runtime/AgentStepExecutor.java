package com.credit.agent.orchestration.runtime;

/**
 * 单步执行器: ACT 阶段的实际动作(F3/F4 的接入点)。
 * 业务含义: 状态机只负责"该不该继续", 具体某一轮查什么、调哪个 Tool、花多少 token
 * 由各域 Agent 实现; 把它做成接口是为了让 F1/F2 能脱离 LLM 单测。
 */
@FunctionalInterface
public interface AgentStepExecutor {

    /**
     * 执行第 stepIndex 步(从 1 开始)。
     *
     * @param stepIndex 本步序号, 从 1 开始, 与 approval_step.step_index 对齐
     * @return 本步观察结果与 token 消耗
     */
    StepResult act(int stepIndex);
}
