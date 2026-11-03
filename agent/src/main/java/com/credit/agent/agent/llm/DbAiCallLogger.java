package com.credit.agent.agent.llm;

import com.credit.agent.domain.billing.TokenCost;
import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import com.credit.agent.infra.persistence.service.AiCallLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 落库版 LLM 调用记录器(H3)。
 * 业务含义: 成本要能按 trace_id 聚合出"这一笔花了多少钱、调了几次", 所以每次调用必须落 ai_call_log;
 * cost 由 domain 的 {@link TokenCost} 按单价确定性算出, 单价未配置时 cost 为 null(绝不瞎估)。
 * 区分 chat 与 embedding: 两者单价不同, embedding 只有输入 token, 混用同一组单价会算错账。
 * 容错: 落库失败只告警, 不影响审批主流程(成本统计丢一条可以接受, 审批中断不可接受)。
 */
@Component
@Primary
public class DbAiCallLogger implements AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(DbAiCallLogger.class);

    private final AiCallLogService aiCallLogService;
    private final BigDecimal promptPricePerK;
    private final BigDecimal completionPricePerK;
    private final BigDecimal embeddingPricePerK;

    public DbAiCallLogger(AiCallLogService aiCallLogService,
                          @Value("${credit-agent.ai.price.prompt-per-k:}") String promptPricePerK,
                          @Value("${credit-agent.ai.price.completion-per-k:}") String completionPricePerK,
                          @Value("${credit-agent.ai.price.embedding-per-k:}") String embeddingPricePerK) {
        this.aiCallLogService = aiCallLogService;
        this.promptPricePerK = parsePrice(promptPricePerK);
        this.completionPricePerK = parsePrice(completionPricePerK);
        this.embeddingPricePerK = parsePrice(embeddingPricePerK);
    }

    @Override
    public void log(AiCallRecord record) {
        if (record == null) {
            return;
        }
        BigDecimal cost = costOf(record);
        log.info("AI_CALL trace_id={} kind={} model={} round={} prompt_tokens={} completion_tokens={} cost={} latency_ms={}",
                record.traceId(), record.kind(), record.model(), record.roundNo(), record.promptTokens(),
                record.completionTokens(), cost == null ? "unknown" : cost.toPlainString(), record.latencyMs());
        try {
            AiCallLog entity = new AiCallLog();
            entity.setTraceId(record.traceId());
            entity.setKind(record.kind() == null ? AiCallRecord.Kind.CHAT.name() : record.kind().name());
            entity.setModel(record.model() == null ? "unknown" : record.model());
            entity.setPromptTokens(record.promptTokens());
            entity.setCompletionTokens(record.completionTokens());
            entity.setCost(cost);
            entity.setLatencyMs(record.latencyMs());
            entity.setRoundNo(record.roundNo());
            aiCallLogService.save(entity);
        } catch (RuntimeException e) {
            log.warn("AI 调用明细落库失败, 已忽略: {}", e.getMessage());
        }
    }

    /** 单价未配置则成本留空, 不用 0 冒充(0 会让成本报表失真)。 */
    private BigDecimal costOf(AiCallRecord record) {
        if (record.kind() == AiCallRecord.Kind.EMBEDDING) {
            return embeddingPricePerK == null ? null
                    : TokenCost.embeddingCost(record.promptTokens(), embeddingPricePerK);
        }
        if (promptPricePerK == null || completionPricePerK == null) {
            return null;
        }
        return TokenCost.cost(record.promptTokens(), record.completionTokens(),
                promptPricePerK, completionPricePerK);
    }

    private static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("模型单价配置非法, 成本将留空: {}", raw);
            return null;
        }
    }
}
