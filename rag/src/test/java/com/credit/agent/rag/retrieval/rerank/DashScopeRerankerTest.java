package com.credit.agent.rag.retrieval.rerank;

import com.credit.agent.rag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 专用重排实现: 端点推导、关闭短路、不可达时降级。
 * 真实接口调用不在这里测(需要 Key), 见 {@code C5RerankTest}(@Tag("live"))。
 */
class DashScopeRerankerTest {

    /** 不可达地址: 用来验证失败路径, connection refused 会立刻返回。 */
    private static final String DEAD_URL = "http://127.0.0.1:1";

    private static DashScopeReranker reranker(boolean enabled, String baseUrl) {
        return new DashScopeReranker(record -> { }, enabled, baseUrl, baseUrl, "k", "k",
                "qwen3-rerank", 20, 200, 800);
    }

    private static List<RetrievedChunk> chunks(int count) {
        List<RetrievedChunk> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(new RetrievedChunk(i, 10L, "制度.pdf", "第一章", "第 " + i + " 段", 1, 1, 0.9 - i * 0.01));
        }
        return list;
    }

    @Test
    @DisplayName("端点推导: chat 基址(/compatible-mode)换成 rerank 的 /compatible-api")
    void resolvesFromChatBaseUrl() {
        assertEquals("https://x.maas.aliyuncs.com/compatible-api/v1/reranks",
                DashScopeReranker.resolveEndpoint(null,
                        "https://x.maas.aliyuncs.com/compatible-mode"));
        assertEquals("https://x.maas.aliyuncs.com/compatible-api/v1/reranks",
                DashScopeReranker.resolveEndpoint(null,
                        "https://x.maas.aliyuncs.com/compatible-mode/v1"));
    }

    @Test
    @DisplayName("端点推导: 只给 host 也能拼对")
    void resolvesFromHostOnly() {
        assertEquals("https://x.maas.aliyuncs.com/compatible-api/v1/reranks",
                DashScopeReranker.resolveEndpoint(null, "https://x.maas.aliyuncs.com/"));
    }

    @Test
    @DisplayName("端点推导: 已给 /compatible-api 时不重复拼")
    void doesNotDuplicatePath() {
        assertEquals("https://x.maas.aliyuncs.com/compatible-api/v1/reranks",
                DashScopeReranker.resolveEndpoint("https://x.maas.aliyuncs.com/compatible-api", null));
    }

    @Test
    @DisplayName("两个基址都没配 -> 启动期直接报错, 而不是运行时 404")
    void rejectsMissingBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> DashScopeReranker.resolveEndpoint(null, null));
    }

    @Test
    @DisplayName("空候选返回空, 不发请求")
    void emptyCandidates() {
        assertTrue(reranker(true, DEAD_URL).rerank("额度", List.of(), 3).isEmpty());
    }

    @Test
    @DisplayName("全局关闭时按原顺序截断, 不发起任何网络调用")
    void disabledReturnsOriginalOrder() {
        List<RetrievedChunk> result = reranker(false, DEAD_URL).rerank("额度", chunks(5), 3);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(RetrievedChunk::chunkId).toList());
    }

    @Test
    @DisplayName("端点不可达时降级为原顺序, 不抛异常")
    void unreachableDegradesToOriginal() {
        List<RetrievedChunk> result = reranker(true, DEAD_URL).rerank("额度", chunks(4), 3);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(RetrievedChunk::chunkId).toList());
    }

    @Test
    @DisplayName("候选数不超过 topN 时也要走正常路径(不因 size<=1 之类的边界丢结果)")
    void singleCandidateKept() {
        List<RetrievedChunk> result = reranker(true, DEAD_URL).rerank("额度", chunks(1), 3);

        assertEquals(1, result.size());
    }
}
