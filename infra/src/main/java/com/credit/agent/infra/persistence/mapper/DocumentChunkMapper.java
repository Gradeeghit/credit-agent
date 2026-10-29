package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切片 Mapper。
 * 业务含义: 只负责切片正文与页码等元数据的读写;
 * embedding 向量的写入与相似度检索必须走 rag 模块的 JdbcTemplate, 不要在本 Mapper 里拼 vector SQL。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
