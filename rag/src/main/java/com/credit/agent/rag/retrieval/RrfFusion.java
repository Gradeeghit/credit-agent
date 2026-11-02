package com.credit.agent.rag.retrieval;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF(Reciprocal Rank Fusion)融合(C3)。
 * 业务含义: 向量分是余弦相似度(有界), BM25 分无上界, 直接加权相加会让 BM25 单方面决定结果;
 * RRF 只用排名 —— 每条命中的得分为 Σ 1/(k + rank), 在两路都靠前的自然胜出,
 * 只在一路出现的也能凭第二名次保留下来。
 * <p>
 * 公式: {@code rrf(d) = Σ_i 1 / (k + rank_i(d))}, rank 从 1 开始, k 默认 60(PRD C3)。
 * 同分时按 chunkId 升序, 保证同一批输入每次输出一致(可复现)。
 */
public final class RrfFusion {

    /** PRD C3 的经验值 */
    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    /**
     * 融合多路排名列表。
     *
     * @param rankedLists 来源名 → 按名次排列的 chunkId 列表(名次从列表下标 0 起算为 rank 1)
     * @param k           平滑常数, 越大越弱化头部差异, 必须大于 0
     * @param limit       返回条数, 必须大于 0
     * @return 按融合分降序的命中, 每条带上它在各路的原始名次
     */
    public static List<FusedHit> fuse(Map<String, List<Long>> rankedLists, int k, int limit) {
        if (k <= 0) {
            throw new IllegalArgumentException("RRF 常数 k 必须大于 0, 实际: " + k);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("返回条数必须大于 0, 实际: " + limit);
        }
        if (rankedLists == null || rankedLists.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Map<String, Integer>> ranks = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : rankedLists.entrySet()) {
            String source = entry.getKey();
            List<Long> ids = entry.getValue();
            if (ids == null) {
                continue;
            }
            for (int index = 0; index < ids.size(); index++) {
                long chunkId = ids.get(index);
                int rank = index + 1;
                scores.merge(chunkId, 1.0 / (k + rank), Double::sum);
                ranks.computeIfAbsent(chunkId, ignored -> new LinkedHashMap<>()).put(source, rank);
            }
        }
        return scores.entrySet().stream()
                .map(entry -> new FusedHit(entry.getKey(), entry.getValue(),
                        Map.copyOf(ranks.getOrDefault(entry.getKey(), Map.of()))))
                .sorted(Comparator.comparingDouble(RrfFusion.FusedHit::score).reversed()
                        .thenComparingLong(RrfFusion.FusedHit::chunkId))
                .limit(limit)
                .toList();
    }

    /**
     * 一条融合命中。
     *
     * @param chunkId document_chunk 主键
     * @param score   融合分, 只用于排序, 与余弦相似度/BM25 分都不可比
     * @param ranks   各来源里的原始名次(从 1 开始), 便于排查"它是靠哪一路进来的"
     */
    public record FusedHit(long chunkId, double score, Map<String, Integer> ranks) {
    }
}
