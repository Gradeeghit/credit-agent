package com.credit.agent.infra.observability;

/**
 * LLM 调用记录器(CLAUDE.md 强制: 所有 LLM 调用必须经此记录)。
 * 业务含义: 审批系统要能回答"这笔单子花了多少钱、调了几次模型";
 * 现阶段先落日志, `ai_call_log` 表建好后换成写库实现, 调用方不需要改。
 */
public interface AiCallLogger {

    /**
     * 记录一次模型调用。
     *
     * @param record 模型、token、耗时; 记录本身失败不得影响主流程
     */
    void log(AiCallRecord record);
}
