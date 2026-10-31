package com.credit.agent.orchestration.runtime;

/**
 * 单步执行结果。
 * 业务含义: 每一步都要回答三件事 —— 这一步看到了什么(observation)、花了多少 token、够不够收工(done);
 * done 由执行方判断(例如"需要的材料都核过了"), 状态机只负责记录与终止。
 *
 * @param done        是否可以收敛, true 表示本轮结束后不必再开下一轮
 * @param tokensUsed  本步消耗的 token(prompt + completion); 负数按 0 处理
 * @param observation 本步观察摘要, 会作为部分结果随终止一起返回
 */
public record StepResult(boolean done, long tokensUsed, String observation) {

    /** 正常一步, 继续下一轮。 */
    public static StepResult more(long tokensUsed, String observation) {
        return new StepResult(false, tokensUsed, observation);
    }

    /** 最后一步, 可以收敛。 */
    public static StepResult done(long tokensUsed, String observation) {
        return new StepResult(true, tokensUsed, observation);
    }
}
