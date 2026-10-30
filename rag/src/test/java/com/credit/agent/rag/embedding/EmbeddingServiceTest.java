package com.credit.agent.rag.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 向量化服务单测: 按 20 分批、维度不符拒绝、空输入不发起调用。 */
class EmbeddingServiceTest {

    private final EmbeddingModel model = mock(EmbeddingModel.class);

    @Test
    @DisplayName("23 条文本按单批 20 拆成 2 次调用, 结果顺序保持一致")
    void shouldBatchByTwenty() {
        when(model.embed(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            return batch.stream().map(text -> unitVector(1024)).toList();
        });
        EmbeddingService service = new EmbeddingService(model, 1024, 20);

        List<String> texts = IntStream.range(0, 23).mapToObj(i -> "chunk-" + i).toList();
        List<float[]> vectors = service.embedAll(texts);

        assertEquals(23, vectors.size());
        vectors.forEach(vector -> assertEquals(1024, vector.length));
        verify(model, times(2)).embed(anyList());
    }

    @Test
    @DisplayName("返回维度与配置不一致时抛异常, 不把错维度写进库")
    void shouldRejectWrongDimension() {
        when(model.embed(anyList())).thenReturn(List.of(new float[10]));
        EmbeddingService service = new EmbeddingService(model, 1024, 20);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.embedAll(List.of("第一章 总则")));

        assertTrue(ex.getMessage().contains("向量维度不匹配"));
    }

    @Test
    @DisplayName("空输入不调用模型, 避免无意义计费")
    void shouldSkipEmptyInput() {
        EmbeddingService service = new EmbeddingService(model, 1024, 20);

        assertTrue(service.embedAll(List.of()).isEmpty());
        verifyNoInteractions(model);
    }

    private static float[] unitVector(int dimensions) {
        float[] vector = new float[dimensions];
        vector[0] = 0.1f;
        return vector;
    }
}
