package com.credit.agent.orchestration.runtime;

/**
 * 非法状态迁移(F1 的可测点)。
 * 业务含义: 状态机是"不可回放的执行过程"的唯一保障 —— 跳过 OBSERVE 直接 REFLECT,
 * 或从 REFLECT 直接 ACT, 都意味着有一步行为了无证据地消失了, 必须立刻崩而不是悄悄放过。
 */
public class IllegalStateTransition extends RuntimeException {

    public IllegalStateTransition(AgentPhase from, AgentPhase to) {
        super("非法状态迁移: " + from + " → " + to);
    }
}
