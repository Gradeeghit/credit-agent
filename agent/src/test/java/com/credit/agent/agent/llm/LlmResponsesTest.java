package com.credit.agent.agent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ChatResponse → 内部契约的映射单测: 文本、token 兜底、空响应。 */
class LlmResponsesTest {

    @Test
    @DisplayName("正常响应取出文本; 缺 usage 时 token 退化为 0 而不是抛错")
    void mapsTextAndFallsBackToZeroTokens() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("结论如下"))));

        LlmAnswer answer = LlmResponses.toAnswer(response);

        assertEquals("结论如下", answer.text());
        assertEquals(0, answer.promptTokens());
        assertEquals(0, answer.completionTokens());
        assertEquals(0, answer.totalTokens());
        assertNotNull(LlmResponses.modelOf(response));
    }

    @Test
    @DisplayName("空响应直接报错, 不让空文本进入报告")
    void emptyResponseFails() {
        assertThrows(LlmCallException.class, () -> LlmResponses.toAnswer(null));
        assertEquals("unknown", LlmResponses.modelOf(null));
    }
}
