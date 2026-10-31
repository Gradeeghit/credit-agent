package com.credit.agent.orchestration.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F2 双终止单测: 步数到顶、token 超预算、两种边界值。 */
class AgentRuntimeTest {

    @Test
    @DisplayName("正常收敛: 第 2 步 done → COMPLETED, 无失败原因")
    void completesWhenExecutorDone() {
        AgentRuntime runtime = new AgentRuntime(8, 20_000);
        AgentRunResult result = runtime.run(step -> step == 2
                ? StepResult.done(1_000, "材料核对完成")
                : StepResult.more(1_000, "补查征信"));

        assertTrue(result.isCompleted());
        assertNull(result.failureReason());
        assertEquals(2, result.steps());
        assertEquals(2_000, result.tokensUsed());
        assertEquals(2, result.observations().size());
    }

    @Test
    @DisplayName("步数到顶: 需要 9 步的任务只执行 8 步, 第 9 步不发起, MAX_STEPS 且保留部分结果")
    void stopsAtMaxSteps() {
        AgentRuntime runtime = new AgentRuntime(8, 100_000);
        AtomicInteger calls = new AtomicInteger();
        AgentRunResult result = runtime.run(step -> {
            calls.incrementAndGet();
            return StepResult.more(10, "第" + step + "步观察");
        });

        assertEquals(8, result.steps());
        assertEquals(8, calls.get(), "第 9 步不允许发起");
        assertEquals(FailureReason.MAX_STEPS, result.failureReason());
        assertEquals(AgentRunResult.STATUS_FAILED, result.status());
        assertEquals(8, result.observations().size(), "终止必须返回部分结果");
    }

    @Test
    @DisplayName("token 超预算: 累计 21000 > 20000 立即终止, BUDGET_EXCEEDED")
    void stopsWhenBudgetExceeded() {
        AgentRuntime runtime = new AgentRuntime(8, 20_000);
        AgentRunResult result = runtime.run(step -> StepResult.more(7_000, "第" + step + "步"));

        assertEquals(3, result.steps());
        assertEquals(21_000, result.tokensUsed());
        assertEquals(FailureReason.BUDGET_EXCEEDED, result.failureReason());
        assertEquals(3, result.observations().size());
    }

    @Test
    @DisplayName("token 恰好等于预算不终止(只有严格超过才停)")
    void exactlyOnBudgetKeepsRunning() {
        AgentRuntime runtime = new AgentRuntime(8, 20_000);
        AgentRunResult result = runtime.run(step -> step == 3
                ? StepResult.done(0, "收工")
                : StepResult.more(10_000, "第" + step + "步"));

        assertTrue(result.isCompleted());
        assertEquals(3, result.steps());
        assertEquals(20_000, result.tokensUsed());
    }

    @Test
    @DisplayName("第 8 步 done 算正常收敛, 不能误判成 MAX_STEPS")
    void doneOnLastAllowedStepCompletes() {
        AgentRuntime runtime = new AgentRuntime(8, 100_000);
        AgentRunResult result = runtime.run(step -> step == 8
                ? StepResult.done(10, "第 8 步收工")
                : StepResult.more(10, "继续"));

        assertTrue(result.isCompleted());
        assertEquals(8, result.steps());
    }

    @Test
    @DisplayName("executor 为空或配置非法时直接拒绝, 不跑空循环")
    void rejectsNullExecutor() {
        AgentRuntime runtime = new AgentRuntime(8, 20_000);

        assertThrows(IllegalArgumentException.class, () -> runtime.run(null));
        assertEquals(8, runtime.maxSteps());
        assertEquals(20_000, runtime.tokenBudget());
    }
}
