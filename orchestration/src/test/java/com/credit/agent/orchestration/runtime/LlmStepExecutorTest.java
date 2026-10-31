package com.credit.agent.orchestration.runtime;

import com.credit.agent.agent.llm.LlmAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型驱动单步执行器单测: token 上报、done 判定、上一轮回答喂给下一轮。 */
class LlmStepExecutorTest {

    @Test
    @DisplayName("模型说收工时 done=true, 并把本轮 token 如实上报")
    void doneComesFromPredicate() {
        LlmStepExecutor executor = new LlmStepExecutor(
                request -> new LlmAnswer("[完成] 结论", 100, 20),
                "trace-1", "你是材料核验 Agent",
                (step, prev) -> "第" + step + "步问题",
                text -> text.contains("[完成]"));

        StepResult result = executor.act(1);

        assertTrue(result.done());
        assertEquals(120, result.tokensUsed());
        assertTrue(result.observation().contains("结论"));
    }

    @Test
    @DisplayName("上一轮回答作为下一轮问题上下文, 避免每步从零开始")
    void previousObservationFeedsNextPrompt() {
        List<String> prompts = new ArrayList<>();
        LlmStepExecutor executor = new LlmStepExecutor(
                request -> new LlmAnswer("已核对身份证", 10, 5),
                "trace-1", "sys",
                (step, prev) -> {
                    prompts.add(step + "|" + prev);
                    return "q" + step;
                },
                text -> false);

        executor.act(1);
        executor.act(2);

        assertEquals("1|", prompts.get(0));
        assertEquals("2|已核对身份证", prompts.get(1));
    }

    @Test
    @DisplayName("接入 AgentRuntime 后: 两轮收敛, 预算按两轮 token 累计")
    void worksWithRuntimeLoop() {
        AgentRuntime runtime = new AgentRuntime(8, 20_000);
        int[] calls = {0};
        LlmStepExecutor executor = new LlmStepExecutor(
                request -> {
                    calls[0]++;
                    return new LlmAnswer(calls[0] >= 2 ? "[完成]" : "继续查", 100, 50);
                },
                "trace-1", "sys",
                (step, prev) -> "q" + step,
                text -> text.contains("[完成]"));

        AgentRunResult result = runtime.run(executor);

        assertTrue(result.isCompleted());
        assertEquals(2, result.steps());
        assertEquals(300, result.tokensUsed());
    }
}
