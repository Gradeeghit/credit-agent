package com.credit.agent.agent.llm;

/**
 * 模型调用失败。
 * 业务含义: 降级路径(H9)的触发点 —— 编排层捕获它后仍要产出"核验清单 + 规则引擎结果"的降级报告,
 * 因此这里只表达"AI 分析不可用", 不吞掉原因。
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
