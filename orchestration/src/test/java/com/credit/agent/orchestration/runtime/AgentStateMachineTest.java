package com.credit.agent.orchestration.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.credit.agent.orchestration.runtime.AgentPhase.ACT;
import static com.credit.agent.orchestration.runtime.AgentPhase.FINISHED;
import static com.credit.agent.orchestration.runtime.AgentPhase.OBSERVE;
import static com.credit.agent.orchestration.runtime.AgentPhase.PLAN;
import static com.credit.agent.orchestration.runtime.AgentPhase.REFLECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1 状态机单测: 合法回环、跳过阶段、终态不可迁出。 */
class AgentStateMachineTest {

    @Test
    @DisplayName("合法回环: PLAN → ACT → OBSERVE → REFLECT → PLAN")
    void legalLoop() {
        AgentStateMachine machine = new AgentStateMachine();
        assertEquals(PLAN, machine.phase());

        machine.moveTo(ACT);
        machine.moveTo(OBSERVE);
        machine.moveTo(REFLECT);
        machine.moveTo(PLAN);

        assertEquals(PLAN, machine.phase());
        assertTrue(!machine.isFinished());
    }

    @Test
    @DisplayName("跳过 ACT: PLAN → OBSERVE 直接抛 IllegalStateTransition")
    void skipActIsIllegal() {
        AgentStateMachine machine = new AgentStateMachine();

        IllegalStateTransition ex = assertThrows(IllegalStateTransition.class, () -> machine.moveTo(OBSERVE));
        assertTrue(ex.getMessage().contains("PLAN"));
        assertEquals(PLAN, machine.phase(), "非法迁移后状态不得改变");
    }

    @Test
    @DisplayName("跳过 OBSERVE: ACT → REFLECT 非法")
    void skipObserveIsIllegal() {
        AgentStateMachine machine = new AgentStateMachine();
        machine.moveTo(ACT);

        assertThrows(IllegalStateTransition.class, () -> machine.moveTo(REFLECT));
    }

    @Test
    @DisplayName("跳过 PLAN: REFLECT → ACT 非法")
    void skipPlanIsIllegal() {
        AgentStateMachine machine = new AgentStateMachine();
        machine.moveTo(ACT);
        machine.moveTo(OBSERVE);
        machine.moveTo(REFLECT);

        assertThrows(IllegalStateTransition.class, () -> machine.moveTo(ACT));
        assertThrows(IllegalStateTransition.class, () -> machine.moveTo(OBSERVE));
    }

    @Test
    @DisplayName("任意阶段可被终止条件推向 FINISHED, 但 FINISHED 不可迁出")
    void finishedIsTerminal() {
        AgentStateMachine machine = new AgentStateMachine();
        machine.moveTo(FINISHED);
        assertTrue(machine.isFinished());

        assertThrows(IllegalStateTransition.class, () -> machine.moveTo(PLAN));
        assertThrows(IllegalStateTransition.class, () -> machine.moveTo(ACT));
    }
}
