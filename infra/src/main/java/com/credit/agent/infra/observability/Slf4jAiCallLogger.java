package com.credit.agent.infra.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日志版 LLM 调用记录器(H3 落地前的临时实现)。
 * 业务含义: 至少保证每次模型调用在日志里可查、可按 trace_id 过滤;
 * 记录动作本身异常只吞掉并告警, 绝不能因为统计失败把审批流程打断。
 */
@Component
public class Slf4jAiCallLogger implements AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAiCallLogger.class);

    @Override
    public void log(AiCallRecord record) {
        if (record == null) {
            return;
        }
        try {
            log.info("AI_CALL trace_id={} model={} prompt_tokens={} completion_tokens={} latency_ms={}",
                    record.traceId(), record.model(), record.promptTokens(),
                    record.completionTokens(), record.latencyMs());
        } catch (RuntimeException e) {
            log.warn("AI 调用日志记录失败, 已忽略: {}", e.getMessage());
        }
    }
}
