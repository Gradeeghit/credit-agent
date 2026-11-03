package com.credit.agent.rag.retrieval.rerank;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C4 重排器: 只重排不新增、失败必须降级、关闭时不发起模型调用。
 */
class LlmRerankerTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiCallLogger callLogger = mock(AiCallLogger.class);

    private LlmReranker reranker(boolean enabled, int maxCandidates) {
        return new LlmReranker(chatModel, callLogger, enabled, "", maxCandidates, 50);
    }

    private static List<RetrievedChunk> chunks(int count) {
        List<RetrievedChunk> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(new RetrievedChunk(i, 10L, "制度.pdf", "第一章", "第 " + i + " 段内容", 1, 1, 0.9 - i * 0.01));
        }
        return list;
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static List<Long> idsOf(List<RetrievedChunk> list) {
        return list.stream().map(RetrievedChunk::chunkId).toList();
    }

    @Test
    @DisplayName("空候选直接返回空, 不发起任何模型调用")
    void emptyCandidatesSkipModel() {
        assertTrue(reranker(true, 20).rerank("额度上限", List.of(), 3).isEmpty());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("全局关闭时按原顺序截断, 不花一次模型调用")
    void disabledReturnsOriginalOrder() {
        List<RetrievedChunk> result = reranker(false, 20).rerank("额度上限", chunks(5), 3);

        assertEquals(List.of(1L, 2L, 3L), idsOf(result));
        verify(chatModel, never()).call(any(Prompt.class));
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("按模型给出的顺序重排, 只保留前 topN")
    void reordersByModelOutput() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"order\":[3,1,2,4,5]}"));

        List<RetrievedChunk> result = reranker(true, 20).rerank("额度上限", chunks(5), 3);

        assertEquals(List.of(3L, 1L, 2L), idsOf(result));
        verify(callLogger).log(any(AiCallRecord.class));
    }

    @Test
    @DisplayName("模型输出不可解析时降级为原顺序(不抛异常)")
    void unparsableOutputFallsBack() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("我觉得第 3 条更相关"));

        List<RetrievedChunk> result = reranker(true, 20).rerank("额度上限", chunks(4), 3);

        assertEquals(List.of(1L, 2L, 3L), idsOf(result));
    }

    @Test
    @DisplayName("模型调用异常时降级为原顺序, 检索链路不受影响")
    void modelFailureFallsBack() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));

        List<RetrievedChunk> result = reranker(true, 20).rerank("额度上限", chunks(4), 2);

        assertEquals(List.of(1L, 2L), idsOf(result));
        verify(callLogger, never()).log(any());
    }

    @Test
    @DisplayName("只对前 max-candidates 条打分, 超出的不进 prompt(控制成本)")
    void onlyTopCandidatesAreScored() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"order\":[1,2,3]}"));

        reranker(true, 3).rerank("额度上限", chunks(10), 3);

        String prompt = capturedPrompt();
        assertTrue(prompt.contains("[3]"), "前 3 条必须进 prompt");
        assertTrue(!prompt.contains("[4]"), "第 4 条不该进 prompt");
    }

    @Test
    @DisplayName("提示词要求编号从 1 起、每个恰好一次, 避免模型漏项")
    void promptStatesNumberingContract() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"order\":[1,2]}"));

        reranker(true, 20).rerank("额度上限", chunks(2), 2);

        String prompt = capturedPrompt();
        assertTrue(prompt.contains("从 1 开始"));
        assertTrue(prompt.contains("只出现一次"));
    }

    private String capturedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call((Prompt) captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        return instructions.get(0).getText();
    }
}
