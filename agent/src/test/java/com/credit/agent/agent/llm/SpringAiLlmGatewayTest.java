package com.credit.agent.agent.llm;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.tool.registry.LoanToolSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** 模型网关单测: 空请求、正常调用(挂工具 + 记成本)、底层异常收敛。 */
class SpringAiLlmGatewayTest {

    private final ChatClient chatClient = mock(ChatClient.class, withSettings().defaultAnswer(
            org.mockito.Answers.RETURNS_DEEP_STUBS));
    private final AiCallLogger callLogger = mock(AiCallLogger.class);
    private final SpringAiLlmGateway gateway =
            new SpringAiLlmGateway(chatClient, new LoanToolSet(new ToolCallback[0]), callLogger);

    @Test
    @DisplayName("空问题直接拒绝, 不发起模型调用也不记成本")
    void blankPromptRejected() {
        assertThrows(LlmCallException.class, () -> gateway.ask(new LlmRequest("t-1", "sys", "  ")));
        verify(chatClient, never()).prompt();
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("正常调用: 挂上 E 模块工具, 返回文本, 并记录一次调用成本")
    void successRecordsCall() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("建议通过"))));
        when(chatClient.prompt().system(anyString()).user(anyString())
                .toolCallbacks(any(ToolCallback[].class)).call().chatResponse()).thenReturn(response);

        LlmAnswer answer = gateway.ask(new LlmRequest("t-1", "sys", "这笔申请怎么看"));

        assertEquals("建议通过", answer.text());
        verify(callLogger).log(any(AiCallRecord.class));
    }

    @Test
    @DisplayName("底层异常收敛成 LlmCallException, 保留原因供降级判断")
    void providerFailureIsWrapped() {
        when(chatClient.prompt()).thenThrow(new IllegalStateException("connection reset"));

        LlmCallException ex = assertThrows(LlmCallException.class,
                () -> gateway.ask(new LlmRequest("t-1", "sys", "问题")));

        assertTrue(ex.getMessage().contains("模型调用失败"));
        assertEquals("connection reset", ex.getCause().getMessage());
        verify(callLogger, never()).log(any());
    }
}
