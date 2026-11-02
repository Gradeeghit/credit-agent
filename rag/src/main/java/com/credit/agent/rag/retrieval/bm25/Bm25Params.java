package com.credit.agent.rag.retrieval.bm25;

/**
 * BM25 参数(C1)。
 * 业务含义: k1 控制词频饱和速度(越大越看重重复出现), b 控制文档长度归一化强度(0=不归一化, 1=完全归一化);
 * PRD C1 定的基准值是 k1=1.5、b=0.75, 可配是为了评测集上做参数对比。
 *
 * @param k1 词频饱和参数, 必须大于 0
 * @param b  长度归一化参数, 取值 [0, 1]
 */
public record Bm25Params(double k1, double b) {

    /** PRD C1 的基准参数。 */
    public static final Bm25Params DEFAULT = new Bm25Params(1.5, 0.75);

    public Bm25Params {
        if (k1 <= 0) {
            throw new IllegalArgumentException("k1 必须大于 0, 实际: " + k1);
        }
        if (b < 0 || b > 1) {
            throw new IllegalArgumentException("b 必须落在 [0,1], 实际: " + b);
        }
    }
}
