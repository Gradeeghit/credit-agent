package com.credit.agent.rag.eval;

import java.util.List;

/**
 * 单条 query 的评测结果(C5)。
 * 业务含义: 只看总体 Recall 无法定位问题, 所以每条都留"是否命中、命中在第几名、召回了什么",
 * 分数下降时能直接看出是语料歧义还是检索退化。
 *
 * @param queryId query 编号
 * @param query   原始问题
 * @param gold    标准答案
 * @param topIds  实际召回的前 k 个切片 ID(按排名)
 * @param rank    第一条 gold 出现的名次(从 1 起); 未命中为 0
 */
public record QueryOutcome(String queryId, String query, List<Long> gold, List<Long> topIds, int rank) {

    /** 是否命中 top-k。 */
    public boolean hit() {
        return rank > 0;
    }

    /** 该条的倒数排名贡献(MRR 分量), 未命中为 0。 */
    public double reciprocalRank() {
        return rank > 0 ? 1.0 / rank : 0.0;
    }
}
