package com.credit.agent.agent.llm;

import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import com.credit.agent.infra.persistence.service.AiCallLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 成本记账单测: chat 与 embedding 走不同单价, 单价缺失时 cost 为 null。 */
class DbAiCallLoggerTest {

    private final AiCallLogService service = mock(AiCallLogService.class);

    @Test
    @DisplayName("CHAT 用对话单价: 1000 输入 + 500 输出 × 1/2 元每千 = 2.000000")
    void chatUsesChatPrices() {
        logger("1", "2", "0.000125")
                .log(new AiCallRecord("t-1", "deepseek", 1000, 500, 120, AiCallRecord.Kind.CHAT));

        AiCallLog saved = saved();
        assertEquals("2.000000", saved.getCost().toPlainString());
        assertEquals("CHAT", saved.getKind());
    }

    @Test
    @DisplayName("EMBEDDING 用向量单价且忽略输出: 8000 × 0.000125 元每千 = 0.001000")
    void embeddingUsesEmbeddingPrice() {
        logger("1", "2", "0.000125")
                .log(new AiCallRecord("t-1", "qwen-embedding", 8000, 0, 40, AiCallRecord.Kind.EMBEDDING));

        AiCallLog saved = saved();
        assertEquals("0.001000", saved.getCost().toPlainString());
        assertEquals("EMBEDDING", saved.getKind());
        assertEquals(0L, saved.getCompletionTokens());
    }

    @Test
    @DisplayName("embedding 单价未配置时 cost 为 null, 不用 0 冒充")
    void missingEmbeddingPriceKeepsCostNull() {
        logger("1", "2", "")
                .log(new AiCallRecord("t-1", "qwen-embedding", 8000, 0, 40, AiCallRecord.Kind.EMBEDDING));

        assertNull(saved().getCost());
    }

    @Test
    @DisplayName("落库失败只告警, 不抛给调用方")
    void saveFailureIsSwallowed() {
        when(service.save(any(AiCallLog.class))).thenThrow(new RuntimeException("db down"));

        logger("1", "2", "0.000125")
                .log(new AiCallRecord("t-1", "deepseek", 10, 10, 5, AiCallRecord.Kind.CHAT));
        // 不抛异常即通过
    }

    private DbAiCallLogger logger(String prompt, String completion, String embedding) {
        return new DbAiCallLogger(service, prompt, completion, embedding);
    }

    private AiCallLog saved() {
        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(service).save(captor.capture());
        return captor.getValue();
    }
}
