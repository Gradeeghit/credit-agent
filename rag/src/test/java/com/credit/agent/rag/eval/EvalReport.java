package com.credit.agent.rag.eval;

import java.util.List;
import java.util.Locale;

/**
 * 一次检索评测的汇总(C5)。
 * 业务含义: 决定"制度域要不要从 VECTOR 切 HYBRID"靠的是同一份 50 条标注下的 Recall@3 对比,
 * 所以报告必须同时给出总体指标、分档指标(字面/改写)与未命中明细。
 *
 * @param label    评测档位名, 如 VECTOR / BM25 / HYBRID
 * @param k        命中窗口
 * @param outcomes 每条 query 的结果
 */
public record EvalReport(String label, int k, List<QueryOutcome> outcomes) {

    public EvalReport {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }

    /** 有效 query 数。 */
    public int total() {
        return outcomes.size();
    }

    /** 命中条数。 */
    public int hits() {
        return (int) outcomes.stream().filter(QueryOutcome::hit).count();
    }

    /** Recall@k = 命中条数 / 总条数。 */
    public double recallAtK() {
        return total() == 0 ? 0.0 : (double) hits() / total();
    }

    /** MRR = 每条 1/首次命中名次 的均值; 未命中记 0。 */
    public double mrr() {
        return total() == 0 ? 0.0 : outcomes.stream().mapToDouble(QueryOutcome::reciprocalRank).average().orElse(0.0);
    }

    /** 未命中的 query, 用于看是语料歧义还是检索退化。 */
    public List<QueryOutcome> missed() {
        return outcomes.stream().filter(o -> !o.hit()).toList();
    }

    /** 单行摘要, 直接打日志。 */
    public String summary() {
        return String.format(Locale.ROOT, "%s Recall@%d=%.4f (%d/%d) MRR=%.4f",
                label, k, recallAtK(), hits(), total(), mrr());
    }
}
