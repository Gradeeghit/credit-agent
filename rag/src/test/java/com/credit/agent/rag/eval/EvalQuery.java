package com.credit.agent.rag.eval;

import java.util.List;

/**
 * 一条标注评测 query(C5)。
 * 业务含义: gold 是"人认为应该召回的那一段", 没有它就算不出 Recall;
 * tag 用来分开看字面重合与同义改写两档 —— BM25 应该在字面档强、改写档弱, 这正是向量路的比较基准。
 *
 * @param id     query 编号
 * @param query  自然语言问题
 * @param gold   标准答案切片 ID(可多条, 命中任一即算召回)
 * @param tag    literal=字面重合; paraphrase=同义改写
 */
public record EvalQuery(String id, String query, List<Long> gold, String tag) {

    public EvalQuery {
        gold = gold == null ? List.of() : List.copyOf(gold);
        if (gold.isEmpty()) {
            throw new IllegalArgumentException("评测 query 缺少 gold: " + id);
        }
    }

    /** 是否同义改写(用于分档统计)。 */
    public boolean isParaphrase() {
        return "paraphrase".equals(tag);
    }
}
