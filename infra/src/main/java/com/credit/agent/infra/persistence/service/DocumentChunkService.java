package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.DocumentChunk;

/**
 * 文档切片持久化服务。
 * 业务含义: 切片正文与页码元数据的读写; 向量化结果不在本服务处理, 由 rag 模块用 JdbcTemplate 写入。
 */
public interface DocumentChunkService extends IService<DocumentChunk> {
}
