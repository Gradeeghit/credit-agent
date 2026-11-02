package com.credit.agent.rag.retrieval.bm25;

import java.util.List;

/**
 * 分词器接口(C1)。
 * 业务含义: BM25 按"词"统计, 中文必须先切词才能算 ID; 接口化是为了让检索逻辑可脱 jieba 单测
 * (测试里直接传固定词表, 不受分词器版本影响)。
 */
public interface Tokenizer {

    /**
     * 把文本切成检索用的词序列。
     * 业务含义: 返回的词应已统一小写、去掉空白与纯标点, 否则同一个词会被统计成多份。
     */
    List<String> tokenize(String text);
}
