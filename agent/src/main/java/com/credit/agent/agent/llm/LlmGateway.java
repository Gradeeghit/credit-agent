package com.credit.agent.agent.llm;

/**
 * 模型调用出口(唯一允许触达 ChatModel 的地方)。
 * 业务含义: 域 Agent 只声明"我要问什么", 由网关统一挂工具、记成本、包超时;
 * 把模型关在接口后面, 单测与降级演练都不需要真实 Key。
 */
@FunctionalInterface
public interface LlmGateway {

    /**
     * 发起一次模型调用。
     *
     * @param request 问题与 trace_id
     * @return 文本与 token 用量
     * @throws LlmCallException 模型不可用、返回为空或调用被中断
     */
    LlmAnswer ask(LlmRequest request);
}
