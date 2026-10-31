package com.credit.agent.orchestration.runtime;

import com.credit.agent.agent.llm.LlmAnswer;
import com.credit.agent.agent.llm.LlmGateway;
import com.credit.agent.agent.llm.LlmRequest;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 用模型驱动单步执行的 {@link AgentStepExecutor} 实现(状态机与 ChatModel 的接缝)。
 * 业务含义: 每一步都是一次"问模型 → 拿到观察 → 交给 REFLECT 判断是否收工";
 * 上一轮的回答会作为下一轮问题的上下文传入, 让 Agent 能真正逐步收窄问题而不是复读。
 * 业务边界的守卫: 收敛判定由外部传入的 donePredicate 决定, 不把"是否结束"交给模型自由发挥。
 */
public class LlmStepExecutor implements AgentStepExecutor {

    private final LlmGateway gateway;
    private final String traceId;
    private final String systemPrompt;
    private final BiFunction<Integer, String, String> userPromptFactory;
    private final Predicate<String> donePredicate;
    private String lastObservation = "";

    public LlmStepExecutor(LlmGateway gateway, String traceId, String systemPrompt,
                           BiFunction<Integer, String, String> userPromptFactory,
                           Predicate<String> donePredicate) {
        this.gateway = gateway;
        this.traceId = traceId;
        this.systemPrompt = systemPrompt;
        this.userPromptFactory = userPromptFactory;
        this.donePredicate = donePredicate;
    }

    /**
     * 执行第 stepIndex 步: 组装问题、调用模型、返回观察与 token。
     * 业务含义: token 用量必须如实上报, 它是 F2 预算终止的唯一输入; 观察文本同时也是部分结果。
     *
     * @param stepIndex 步序号, 从 1 开始
     * @return done 由 donePredicate 判定, tokensUsed 为本次调用总量
     */
    @Override
    public StepResult act(int stepIndex) {
        String prompt = userPromptFactory.apply(stepIndex, lastObservation);
        LlmAnswer answer = gateway.ask(new LlmRequest(traceId, systemPrompt, prompt));
        lastObservation = answer.text();
        return new StepResult(donePredicate.test(lastObservation), answer.totalTokens(), lastObservation);
    }
}
