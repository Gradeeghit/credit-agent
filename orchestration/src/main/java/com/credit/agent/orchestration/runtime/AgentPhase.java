package com.credit.agent.orchestration.runtime;

/**
 * Agent 执行阶段(F1)。
 * 业务含义: 一个审批分析回合由四步构成 ——
 * PLAN(决定下一步查什么) → ACT(调工具/读材料) → OBSERVE(消化结果) → REFLECT(判断是否还需要继续);
 * REFLECT 判定"还不够"就回到 PLAN 开下一轮, 判定"够了"就进 FINISHED。
 */
public enum AgentPhase {

    /** 决定本轮要做什么 */
    PLAN,

    /** 执行: 调 Tool / 检索 / 读材料 */
    ACT,

    /** 观察: 消化本步结果, 不产生新动作 */
    OBSERVE,

    /** 反思: 判断是否可以收敛 */
    REFLECT,

    /** 终态: 已产出结果或已被终止条件打断 */
    FINISHED
}
