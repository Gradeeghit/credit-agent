package com.credit.agent.rag.chunk;

import com.credit.agent.rag.parse.ParsedPage;

import java.util.List;

/**
 * 切分策略(B4)。
 * 业务含义: 切分方式会随语料变化(制度文档按标题层级, 业务材料可能按页),
 * 因此固化成接口, 替换实现不改调用方; 策略实现必须是纯函数, 不碰 DB 与 LLM。
 */
public interface ChunkingStrategy {

    /**
     * 把逐页文本切成可入库的切片。
     *
     * @param pages 解析器产出的逐页文本, 页码与字符偏移必须有效
     * @return 按原文顺序排列的切片; 无有效文本时返回空列表
     */
    List<Chunk> split(List<ParsedPage> pages);
}
