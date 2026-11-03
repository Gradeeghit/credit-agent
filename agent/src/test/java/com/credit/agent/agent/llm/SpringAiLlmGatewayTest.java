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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 模型网关单测: 空请求、按域挂工具、强制工具只在首轮、逐轮记账与 token 累计、轮次上限、异常收敛。
 * 重点覆盖"一次 ask 在供应商侧是多轮计费"这一事实 —— 每轮必须各记一条, 总量必须累加。
 */
class SpringAiLlmGatewayTest {

    private static final String MODEL = "deepseek-v4.1-flash-1017";
    private static final int MAX_ROUNDS = 4;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
    private final AiCallLogger callLogger = mock(AiCallLogger.class);

    private SpringAiLlmGateway gateway(boolean forceToolEnabled) {
        return gateway(forceToolEnabled, MAX_ROUNDS);
    }

    private SpringAiLlmGateway gateway(boolean forceToolEnabled, int maxToolRounds) {
        return new SpringAiLlmGateway(chatModel, toolCallingManager, allTools(), callLogger,
                MODEL, forceToolEnabled, maxToolRounds);
    }

    private static LoanToolSet allTools() {
        return new LoanToolRegistry().loanToolSet(new ProductTools(mock(ProductService.class)),
                new PolicyTools(mock(VectorRetriever.class), 1000));
    }

    /** 普通文本回答, 带 usage。 */
    private static ChatResponse reply(String text, int promptTokens, int completionTokens) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                metadata(promptTokens, completionTokens));
    }

    /** 模型要求调用工具的轮次: 只有 tool_calls, 没有正文。 */
    private static ChatResponse toolCallReply(String toolName, String arguments,
                                              int promptTokens, int completionTokens) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", toolName, arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)),
                metadata(promptTokens, completionTokens));
    }

    private static ChatResponseMetadata metadata(int promptTokens, int completionTokens) {
        return ChatResponseMetadata.builder()
                .model(MODEL)
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();
    }

    /** 工具执行结果: 只需给出下一轮要发送的历史。 */
    private static ToolExecutionResult toolResult() {
        ToolExecutionResult result = mock(ToolExecutionResult.class);
        when(result.conversationHistory()).thenReturn(List.of(
                new SystemMessage("sys"), new UserMessage("问题"), new AssistantMessage("")));
        return result;
    }

    private List<AiCallRecord> loggedRecords(int expected) {
        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callLogger, times(expected)).log(captor.capture());
        return captor.getAllValues();
    }

    private List<Prompt> capturedPrompts(int expected) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        // ChatModel.call 有多个重载, 必须显式转型否则泛型推断有歧义
        verify(chatModel, times(expected)).call((Prompt) captor.capture());
        return captor.getAllValues();
    }

    private static OpenAiChatOptions optionsOf(Prompt prompt) {
        return (OpenAiChatOptions) prompt.getOptions();
    }

    @Test
    @DisplayName("空问题直接拒绝, 不发起模型调用也不记成本")
    void blankPromptRejected() {
        assertThrows(LlmCallException.class, () -> gateway(true).ask(new LlmRequest("t-1", "sys", "  ")));

        verifyNoInteractions(chatModel);
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("无工具的单轮回答: 只记 1 条, round=1, token 与响应一致")
    void singleRoundLogsOneRecord() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("建议通过", 500, 60));

        LlmAnswer answer = gateway(true).ask(new LlmRequest("t-1", "sys", "这笔申请怎么看"));

        assertEquals("建议通过", answer.text());
        assertEquals(500, answer.promptTokens());
        assertEquals(60, answer.completionTokens());

        List<AiCallRecord> records = loggedRecords(1);
        assertEquals(1, records.get(0).roundNo());
        assertEquals(AiCallRecord.Kind.CHAT, records.get(0).kind());
        assertEquals(500, records.get(0).promptTokens());
    }

    @Test
    @DisplayName("工具循环: 每轮各记一条, 返回的 token 是各轮之和(成本不再只算最后一轮)")
    void toolLoopLogsEveryRoundAndSumsTokens() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallReply("queryProduct", "{\"productId\":1}", 100, 20))
                .thenReturn(reply("额度按收入 15 倍", 300, 50));
        // 先取出再 stub: toolResult() 内部也有 when(...), 直接塞进 thenReturn 会触发 UnfinishedStubbing
        ToolExecutionResult executed = toolResult();
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executed);

        LlmAnswer answer = gateway(true)
                .ask(new LlmRequest("t-1", "sys", "产品参数是什么", List.of("queryProduct")));

        // 100+300 / 20+50: 只记最后一轮会少报 100/20
        assertEquals(400, answer.promptTokens());
        assertEquals(70, answer.completionTokens());
        assertEquals("额度按收入 15 倍", answer.text());

        List<AiCallRecord> records = loggedRecords(2);
        assertEquals(1, records.get(0).roundNo());
        assertEquals(2, records.get(1).roundNo());
        assertEquals(100, records.get(0).promptTokens());
        assertEquals(300, records.get(1).promptTokens());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("强制工具只在第 1 轮下发 tool_choice=required, 后续轮次必须撤掉(否则必然死循环)")
    void forceToolOnlyOnFirstRound() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallReply("searchPolicy", "{\"keyword\":\"逾期\"}", 100, 20))
                .thenReturn(reply("依据见引用", 300, 50));
        // 先取出再 stub: toolResult() 内部也有 when(...), 直接塞进 thenReturn 会触发 UnfinishedStubbing
        ToolExecutionResult executed = toolResult();
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executed);

        gateway(true).ask(new LlmRequest("t-1", "sys", "逾期几次算不良", List.of("searchPolicy"), true));

        List<Prompt> prompts = capturedPrompts(2);
        assertEquals("required", optionsOf(prompts.get(0)).getToolChoice());
        assertNull(optionsOf(prompts.get(1)).getToolChoice());
        assertEquals(MODEL, optionsOf(prompts.get(0)).getModel());
    }

    @Test
    @DisplayName("必须关掉框架内置工具执行, 否则一次 call 就把所有轮次吞掉, 中间轮无法记账")
    void internalToolExecutionDisabled() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("建议通过", 100, 10));

        gateway(true).ask(new LlmRequest("t-1", "sys", "问题"));

        assertEquals(Boolean.FALSE, optionsOf(capturedPrompts(1).get(0)).getInternalToolExecutionEnabled());
    }

    @Test
    @DisplayName("按域挂工具: 制度域只挂 searchPolicy, 不会把 queryProduct 一起塞给模型")
    void mountsOnlyDomainSubset() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("依据见引用", 100, 10));

        gateway(true).ask(new LlmRequest("t-1", "sys", "逾期几次算不良", List.of("searchPolicy")));

        List<?> callbacks = optionsOf(capturedPrompts(1).get(0)).getToolCallbacks();
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("不指定子集时挂全量(旧调用方语义), 保证 LlmStepExecutor 等不改也能用")
    void noSubsetMeansAllTools() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("建议通过", 100, 10));

        gateway(true).ask(new LlmRequest("t-1", "sys", "这笔申请怎么看"));

        assertEquals(2, optionsOf(capturedPrompts(1).get(0)).getToolCallbacks().size());
    }

    @Test
    @DisplayName("工具轮次超限: 抛异常中断, 但已发生的轮次都要记账(钱已经花了)")
    void maxRoundsExceededStillLogsSpentRounds() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallReply("queryProduct", "{\"productId\":1}", 100, 20));
        // 先取出再 stub: toolResult() 内部也有 when(...), 直接塞进 thenReturn 会触发 UnfinishedStubbing
        ToolExecutionResult executed = toolResult();
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executed);

        LlmCallException ex = assertThrows(LlmCallException.class,
                () -> gateway(true, 2).ask(new LlmRequest("t-1", "sys", "问题", List.of("queryProduct"))));

        assertTrue(ex.getMessage().contains("轮次超限"));
        assertEquals(2, loggedRecords(2).size());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("底层异常收敛成 LlmCallException, 保留原因供降级判断")
    void providerFailureIsWrapped() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));

        LlmCallException ex = assertThrows(LlmCallException.class,
                () -> gateway(true).ask(new LlmRequest("t-1", "sys", "问题")));

        assertTrue(ex.getMessage().contains("模型调用失败"));
        assertEquals("connection reset", ex.getCause().getMessage());
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("工具执行后历史为空: 视为异常, 不能拿空历史继续发请求")
    void emptyHistoryAfterToolCallRejected() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallReply("queryProduct", "{\"productId\":1}", 100, 20));
        ToolExecutionResult empty = mock(ToolExecutionResult.class);
        when(empty.conversationHistory()).thenReturn(List.of());
        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class))).thenReturn(empty);

        LlmCallException ex = assertThrows(LlmCallException.class,
                () -> gateway(true).ask(new LlmRequest("t-1", "sys", "问题", List.of("queryProduct"))));

        assertTrue(ex.getMessage().contains("对话历史为空"));
    }

    @Test
    @DisplayName("提示词组装: system 为空时不下发空 system 消息, 只保留 user")
    void blankSystemPromptOmitted() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("好", 10, 5));

        gateway(true).ask(new LlmRequest("t-1", "  ", "问题"));

        Prompt prompt = capturedPrompts(1).get(0);
        assertEquals(1, prompt.getInstructions().size());
        assertTrue(prompt.getInstructions().get(0) instanceof UserMessage);
    }

    @Test
    @DisplayName("选项类型断言: 网关下发的是 OpenAiChatOptions, 保证 tool_choice 等字段真正生效")
    void optionsAreOpenAiSpecific() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("好", 10, 5));

        gateway(true).ask(new LlmRequest("t-1", "sys", "问题"));

        ChatOptions options = capturedPrompts(1).get(0).getOptions();
        assertTrue(options instanceof OpenAiChatOptions);
    }
}
