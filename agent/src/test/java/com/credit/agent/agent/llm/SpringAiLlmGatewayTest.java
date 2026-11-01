package com.credit.agent.agent.llm;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.rag.retrieval.VectorRetriever;
import com.credit.agent.tool.policy.PolicyTools;
import com.credit.agent.tool.product.ProductTools;
import com.credit.agent.tool.registry.LoanToolRegistry;
import com.credit.agent.tool.registry.LoanToolSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** 模型网关单测: 空请求、按域挂工具、强制工具、全量兜底、底层异常收敛。 */
class SpringAiLlmGatewayTest {

    private static final String MODEL = "deepseek-v4.1-flash-1017";

    private final ChatClient chatClient = mock(ChatClient.class, withSettings().defaultAnswer(
            org.mockito.Answers.RETURNS_DEEP_STUBS));
    private final AiCallLogger callLogger = mock(AiCallLogger.class);

    private SpringAiLlmGateway gateway(boolean forceToolEnabled) {
        return new SpringAiLlmGateway(chatClient, allTools(), callLogger, MODEL, forceToolEnabled);
    }

    private static LoanToolSet allTools() {
        return new LoanToolRegistry().loanToolSet(new ProductTools(mock(ProductService.class)),
                new PolicyTools(mock(VectorRetriever.class), 1000));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("空问题直接拒绝, 不发起模型调用也不记成本")
    void blankPromptRejected() {
        assertThrows(LlmCallException.class, () -> gateway(true).ask(new LlmRequest("t-1", "sys", "  ")));

        verify(chatClient, never()).prompt();
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("按域挂工具: 制度域只挂 searchPolicy, 不会把 queryProduct 一起塞给模型")
    void mountsOnlyDomainSubset() {
        ChatClient.ChatClientRequestSpec spec = stubToolCall(reply("依据见引用"));

        LlmAnswer answer = gateway(true)
                .ask(new LlmRequest("t-1", "sys", "逾期几次算不良", List.of("searchPolicy")));

        ArgumentCaptor<ToolCallback[]> captor = ArgumentCaptor.forClass(ToolCallback[].class);
        verify(spec).toolCallbacks(captor.capture());
        assertEquals(1, captor.getValue().length);
        assertEquals("searchPolicy", captor.getValue()[0].getToolDefinition().name());
        assertEquals("依据见引用", answer.text());
        verify(callLogger).log(any(AiCallRecord.class));
    }

    @Test
    @DisplayName("强制工具: 下发 tool_choice=required, 且显式带上 model")
    void forceToolSendsRequiredChoice() {
        ChatClient.ChatClientRequestSpec spec = stubForcedCall(reply("依据见引用"));

        gateway(true).ask(new LlmRequest("t-1", "sys", "逾期几次算不良", List.of("searchPolicy"), true));

        ArgumentCaptor<ChatOptions> captor = ArgumentCaptor.forClass(ChatOptions.class);
        verify(spec).options(captor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) captor.getValue();
        assertEquals("required", options.getToolChoice());
        assertEquals(MODEL, options.getModel());
    }

    @Test
    @DisplayName("强制开关关闭时不下发 tool_choice(端点不支持时整体退回模型自主)")
    void forceToolCanBeDisabled() {
        ChatClient.ChatClientRequestSpec spec = stubToolCall(reply("依据见引用"));

        gateway(false).ask(new LlmRequest("t-1", "sys", "逾期几次算不良", List.of("searchPolicy"), true));

        verify(spec, never()).options(any());
        verify(spec).toolCallbacks(any(ToolCallback[].class));
    }

    @Test
    @DisplayName("不指定子集时挂全量(旧调用方语义), 保证 LlmStepExecutor 等不改也能用")
    void noSubsetMeansAllTools() {
        ChatClient.ChatClientRequestSpec spec = stubToolCall(reply("建议通过"));

        gateway(true).ask(new LlmRequest("t-1", "sys", "这笔申请怎么看"));

        ArgumentCaptor<ToolCallback[]> captor = ArgumentCaptor.forClass(ToolCallback[].class);
        verify(spec).toolCallbacks(captor.capture());
        assertEquals(2, captor.getValue().length);
    }

    @Test
    @DisplayName("底层异常收敛成 LlmCallException, 保留原因供降级判断")
    void providerFailureIsWrapped() {
        when(chatClient.prompt()).thenThrow(new IllegalStateException("connection reset"));

        LlmCallException ex = assertThrows(LlmCallException.class,
                () -> gateway(true).ask(new LlmRequest("t-1", "sys", "问题")));

        assertTrue(ex.getMessage().contains("模型调用失败"));
        assertEquals("connection reset", ex.getCause().getMessage());
        verify(callLogger, never()).log(any());
    }

    /** 打桩并清掉打桩期间产生的调用记录, 免得 verify 把 when(...) 自己算进去。 */
    private ChatClient.ChatClientRequestSpec stubToolCall(ChatResponse response) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().system(anyString()).user(anyString());
        when(spec.toolCallbacks(any(ToolCallback[].class)).call().chatResponse()).thenReturn(response);
        clearInvocations(spec);
        return spec;
    }

    /** 强制工具时链路上多了 options(...) 一环, 单独打桩。 */
    private ChatClient.ChatClientRequestSpec stubForcedCall(ChatResponse response) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().system(anyString()).user(anyString());
        when(spec.options(any(ChatOptions.class)).toolCallbacks(any(ToolCallback[].class))
                .call().chatResponse()).thenReturn(response);
        clearInvocations(spec);
        return spec;
    }
}
