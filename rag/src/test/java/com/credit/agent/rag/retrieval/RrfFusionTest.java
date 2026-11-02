package com.credit.agent.rag.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RRF 融合单测(PRD C3): 排名累加、单路命中保留、同分可复现、参数校验。 */
class RrfFusionTest {

    private static final int K = RrfFusion.DEFAULT_K;

    @Test
    @DisplayName("两路名次累加: 双路靠前的排最前, 只在单路出现的也能留下来")
    void fuseByReciprocalRank() {
        Map<String, List<Long>> lists = new LinkedHashMap<>();
        lists.put("vector", List.of(10L, 20L, 30L));
        lists.put("bm25", List.of(30L, 10L));

        List<RrfFusion.FusedHit> hits = RrfFusion.fuse(lists, K, 10);

        assertEquals(3, hits.size());
        // 10: 1/61 + 1/62 = 0.032522
        assertEquals(10L, hits.get(0).chunkId());
        assertEquals(0.032522, hits.get(0).score(), 1e-6);
        // 30: 1/63 + 1/61 = 0.032266
        assertEquals(30L, hits.get(1).chunkId());
        assertEquals(0.032266, hits.get(1).score(), 1e-6);
        // 20: 只在向量路出现, 仍凭第二名次保留
        assertEquals(20L, hits.get(2).chunkId());
        assertEquals(0.016129, hits.get(2).score(), 1e-6);
        assertEquals(Map.of("vector", 1, "bm25", 2), hits.get(0).ranks());
    }

    @Test
    @DisplayName("两路完全对称时按 chunkId 升序, 保证同一输入每次输出一致")
    void tieBreaksByChunkId() {
        Map<String, List<Long>> lists = new LinkedHashMap<>();
        lists.put("vector", List.of(2L, 1L));
        lists.put("bm25", List.of(1L, 2L));

        List<RrfFusion.FusedHit> hits = RrfFusion.fuse(lists, K, 10);

        assertEquals(1L, hits.get(0).chunkId());
        assertEquals(2L, hits.get(1).chunkId());
    }

    @Test
    @DisplayName("limit 生效, 空输入返回空列表")
    void limitsAndEmptyInput() {
        Map<String, List<Long>> lists = new LinkedHashMap<>();
        lists.put("vector", List.of(1L, 2L, 3L, 4L));

        assertEquals(2, RrfFusion.fuse(lists, K, 2).size());
        assertTrue(RrfFusion.fuse(Map.of(), K, 10).isEmpty());
        assertTrue(RrfFusion.fuse(null, K, 10).isEmpty());
    }

    @Test
    @DisplayName("k 或 limit 非法时直接拒绝, 避免静默算出错误排名")
    void rejectInvalidParams() {
        Map<String, List<Long>> lists = Map.of("vector", List.of(1L));

        assertThrows(IllegalArgumentException.class, () -> RrfFusion.fuse(lists, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> RrfFusion.fuse(lists, K, 0));
    }
}
