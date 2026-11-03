package com.credit.agent.rag.retrieval.rerank;

import com.credit.agent.rag.retrieval.RetrievedChunk;

import java.util.List;

/**
 * 召回结果重排器(C4)。
 * 业务含义: 向量/BM25 都是"粗排", 只看 query 与片段的相似度, 不理解条款语义是否真的回答了问题;
 * 重排这一层允许用更贵但更准的判断(LLM 打分)修正 top-N 的顺序, 把真正相关的顶到前面。
 * <p>
 * 契约(重要):
 * <ul>
 *   <li>**只重排、不新增** —— 返回的每条都必须来自 {@code candidates}, 不允许模型"发明"片段;</li>
 *   <li>**失败必须降级** —— 任何异常/解析失败都返回原顺序, 绝不抛给上层, 否则检索一挂整单审批就废;</li>
 *   <li>**返回长度 ≤ topN** —— 重排的目的是精排, 不是扩召回。</li>
 * </ul>
 */
public interface Reranker {

    /**
     * 对候选片段重排。
     *
     * @param query      原始问题
     * @param candidates 粗排候选, 按原相关性降序
     * @param topN       返回条数
     * @return 重排后的前 topN 条; 无法重排时原样返回(截断到 topN)
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}
