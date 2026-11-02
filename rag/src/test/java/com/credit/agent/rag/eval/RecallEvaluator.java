package com.credit.agent.rag.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 检索指标计算(C5)。
 * 业务含义: 评测器只认"问题 → 排名靠前的切片 ID"这一个契约, 不关心是向量、BM25 还是 RRF,
 * 这样三档可以用同一份标注、同一套口径比较, 结果才可对比。
 * 口径: 多条 gold 命中任一即算召回; 名次取第一条 gold 出现的位置(从 1 起)。
 */
public final class RecallEvaluator {

    private RecallEvaluator() {
    }

    /**
     * 跑一次评测。
     *
     * @param label     档位名(VECTOR / BM25 / HYBRID)
     * @param queries   标注 query
     * @param retriever 检索函数: 问题 → 按排名给出的切片 ID
     * @param k         命中窗口(Recall@k)
     * @return 汇总报告
     */
    public static EvalReport evaluate(String label, List<EvalQuery> queries,
                                      Function<String, List<Long>> retriever, int k) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("评测 query 不能为空");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须为正数");
        }
        List<QueryOutcome> outcomes = new ArrayList<>(queries.size());
        for (EvalQuery query : queries) {
            List<Long> ranked = retriever.apply(query.query());
            List<Long> topK = ranked == null ? List.of()
                    : List.copyOf(ranked.subList(0, Math.min(k, ranked.size())));
            outcomes.add(new QueryOutcome(query.id(), query.query(), query.gold(), topK,
                    firstGoldRank(topK, query.gold())));
        }
        return new EvalReport(label, k, outcomes);
    }

    /** 第一条 gold 在 top-k 中的名次; 未出现返回 0。 */
    static int firstGoldRank(List<Long> topK, List<Long> gold) {
        for (int i = 0; i < topK.size(); i++) {
            if (gold.contains(topK.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }
}
