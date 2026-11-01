package com.credit.agent.tool.registry;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 按工具名取子集(F3 分域降载: 制度域只挂 searchPolicy, 其他域只挂 queryProduct)。
     * 业务含义: 名字必须来自 {@link LoanToolRegistry} 的登记 —— 拼错名单直接抛错,
     * 否则该域会"静默没有工具", 提示词里写了工具名却调不到, 排查代价极高。
     *
     * @param names 需要的工具名
     * @return 只含指定工具的新集合
     * @throws IllegalArgumentException 出现未登记的工具名
     */
    public LoanToolSet subset(String... names) {
        List<ToolCallback> picked = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("工具名不能为空");
            }
            boolean found = false;
            for (ToolCallback callback : callbacks == null ? new ToolCallback[0] : callbacks) {
                if (name.equals(callback.getToolDefinition().name())) {
                    picked.add(callback);
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("工具未登记: " + name + ", 已登记: "
                        + String.join(", ", toolNames()));
            }
        }
        return new LoanToolSet(picked.toArray(new ToolCallback[0]));
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
