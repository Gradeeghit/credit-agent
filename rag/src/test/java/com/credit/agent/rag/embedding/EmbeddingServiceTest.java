package com.credit.agent.rag.embedding;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 向量化服务单测: 分批、维度断言、空输入、embedding 成本记账与记账容错。 */
class EmbeddingServiceTest {

    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final AiCallLogger aiCallLogger = mock(AiCallLogger.class);

    @Test
    @DisplayName("23 条文本按单批 20 拆成 2 次调用, 每批各记一条账")
    void shouldBatchByTwentyAndRecordPerBatch() {
        when(model.embedForResponse(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            return response(vectors(batch.size(), 1024), 10);
        });

        List<String> texts = IntStream.range(0, 23).mapToObj(i -> "chunk-" + i).toList();
        List<float[]> vectors = service().embedAll(texts);

        assertEquals(23, vectors.size());
        vectors.forEach(vector -> assertEquals(1024, vector.length));
        verify(model, times(2)).embedForResponse(anyList());
        verify(aiCallLogger, times(2)).log(any());
    }

    @Test
    @DisplayName("返回维度与配置不一致时抛异常, 不把错维度写进库")
    void shouldRejectWrongDimension() {
        when(model.embedForResponse(anyList())).thenReturn(response(vectors(1, 10), 5));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service().embedAll(List.of("第一章 总则")));

        assertTrue(ex.getMessage().contains("向量维度不匹配"));
    }

    @Test
    @DisplayName("空输入不调用模型, 也不记账")
    void shouldSkipEmptyInput() {
        assertTrue(service().embedAll(List.of()).isEmpty());
        verifyNoInteractions(model, aiCallLogger);
    }

    @Test
    @DisplayName("每次真实调用记一条 kind=EMBEDDING 的账, token 取自响应 usage")
    void shouldRecordEmbeddingCost() {
        when(model.embedForResponse(anyList())).thenReturn(response(vectors(1, 1024), 37));

        service().embedOne("逾期几次算不良");

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(aiCallLogger).log(captor.capture());
        AiCallRecord record = captor.getValue();
        assertEquals(AiCallRecord.Kind.EMBEDDING, record.kind());
        assertEquals(37L, record.promptTokens());
        assertEquals(0L, record.completionTokens());
        assertEquals("qwen-test", record.model());
    }

    @Test
    @DisplayName("记账失败只告警, 不影响向量化结果")
    void recordFailureShouldNotBreakEmbedding() {
        when(model.embedForResponse(anyList())).thenReturn(response(vectors(1, 1024), 8));
        doThrow(new RuntimeException("db down")).when(aiCallLogger).log(any());

        float[] vector = service().embedOne("第一章 总则");

        assertEquals(1024, vector.length);
    }

    private EmbeddingService service() {
        return new EmbeddingService(model, aiCallLogger, "qwen-test", 1024, 20);
    }

    private static float[][] vectors(int count, int dimensions) {
        float[][] result = new float[count][dimensions];
        for (int i = 0; i < count; i++) {
            result[i][0] = 0.1f;
        }
        return result;
    }

    private static EmbeddingResponse response(float[][] vectors, int promptTokens) {
        List<Embedding> embeddings = new ArrayList<>(vectors.length);
        for (int i = 0; i < vectors.length; i++) {
            embeddings.add(new Embedding(vectors[i], i));
        }
        return new EmbeddingResponse(embeddings,
                new EmbeddingResponseMetadata("qwen-test", new FakeUsage(promptTokens, 0)));
    }

    /** 测试用的 Usage 实现: 不在 thenReturn(...) 里嵌套 mock, 否则 Mockito 报 UnfinishedStubbing。 */
    private record FakeUsage(Integer promptTokens, Integer completionTokens) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
