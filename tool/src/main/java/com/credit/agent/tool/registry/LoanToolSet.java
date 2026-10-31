package com.credit.agent.tool.registry;

import org.springframework.ai.tool.ToolCallback;

/**
 * 已注册工具的集合(E 模块对 Agent 的唯一出口)。
 * 业务含义: Agent 只允许挂载这里列出的工具, 不允许临时 new 一个工具塞给模型;
 * 用对象包住数组而不是直接暴露 ToolCallback[], 是为了让"挂了哪些工具"可被单测点名断言。
 *
 * @param callbacks Spring AI 可直接挂到 ChatClient 的 Function Calling 回调
 */
public record LoanToolSet(ToolCallback[] callbacks) {

    /** 已挂载工具数量, 供启动自检与日志使用。 */
    public int size() {
        return callbacks == null ? 0 : callbacks.length;
    }

    /** 工具名清单, 便于日志与验收时核对(如 queryProduct / searchPolicy)。 */
    public String[] toolNames() {
        if (callbacks == null) {
            return new String[0];
        }
        String[] names = new String[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            names[i] = callbacks[i].getToolDefinition().name();
        }
        return names;
    }
}
