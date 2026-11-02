package com.credit.agent.rag.retrieval;

import java.util.Locale;

/**
 * 检索模式(C3)。
 * 业务含义: 同一批语料要能按三种方式跑, 才能在评测集上对比"纯向量 / 纯词面 / 融合"的召回差异;
 * 三种模式共用同一套过滤条件(current + 材料类型), 差别只在召回与排序。
 */
public enum SearchMode {

    /** 仅向量召回(语义相近) */
    VECTOR,

    /** 仅 BM25 召回(词面命中) */
    BM25,

    /** 两路各取候选后 RRF 融合(默认) */
    HYBRID;

    /**
     * 解析模式文本, 空值按 HYBRID 处理。
     * 业务含义: 默认走融合, 因为它在绝大多数场景下不劣于单路; 非法值必须报错而不是静默降级,
     * 否则评测时会把"拼错的模式"当成"融合没有提升"。
     */
    public static SearchMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return HYBRID;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode 只能是 VECTOR / BM25 / HYBRID, 实际: " + raw);
        }
    }
}
