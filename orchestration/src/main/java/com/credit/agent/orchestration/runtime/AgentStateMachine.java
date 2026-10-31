package com.credit.agent.orchestration.runtime;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent 执行状态机(F1)。
 * 业务含义: 强制"每一步都留下证据" —— PLAN 只能进 ACT, ACT 只能进 OBSERVE,
 * OBSERVE 只能进 REFLECT, REFLECT 才能决定回 PLAN 还是收尾;
 * 任何状态都可以被终止条件直接推向 FINISHED(预算/步数到顶), 但 FINISHED 是终态, 不可再迁出。
 * 纯对象、无线程安全要求, 每次执行 run 都新建一个实例。
 */
public class AgentStateMachine {

    private static final Map<AgentPhase, Set<AgentPhase>> ALLOWED = allowedTransitions();

    private AgentPhase phase = AgentPhase.PLAN;

    /** 当前阶段; 新建实例即处于 PLAN, 表示"计划尚未执行"。 */
    public AgentPhase phase() {
        return phase;
    }

    /** 是否已进入终态。 */
    public boolean isFinished() {
        return phase == AgentPhase.FINISHED;
    }

    /**
     * 迁移到下一阶段; 非法迁移直接抛异常, 不做任何兜底。
     * 业务含义: 让"跳过观察"或"跳过反思"这类逻辑错误在第一次运行时暴露, 而不是污染报告。
     *
     * @param target 目标阶段
     * @throws IllegalStateTransition 迁移不在白名单内
     */
    public void moveTo(AgentPhase target) {
        if (target == null || !ALLOWED.getOrDefault(phase, Set.of()).contains(target)) {
            throw new IllegalStateTransition(phase, target);
        }
        this.phase = target;
    }

    /** 白名单: 四态循环 + 任意态可被终止 + FINISHED 不可迁出。 */
    private static Map<AgentPhase, Set<AgentPhase>> allowedTransitions() {
        Map<AgentPhase, Set<AgentPhase>> map = new EnumMap<>(AgentPhase.class);
        map.put(AgentPhase.PLAN, EnumSet.of(AgentPhase.ACT, AgentPhase.FINISHED));
        map.put(AgentPhase.ACT, EnumSet.of(AgentPhase.OBSERVE, AgentPhase.FINISHED));
        map.put(AgentPhase.OBSERVE, EnumSet.of(AgentPhase.REFLECT, AgentPhase.FINISHED));
        map.put(AgentPhase.REFLECT, EnumSet.of(AgentPhase.PLAN, AgentPhase.FINISHED));
        map.put(AgentPhase.FINISHED, EnumSet.noneOf(AgentPhase.class));
        return map;
    }
}
