package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import com.credit.agent.infra.persistence.mapper.DocumentChunkMapper;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import org.springframework.stereotype.Service;

/**
 * 文档切片持久化实现。
 * 业务含义: 只处理切片正文与页码元数据; embedding 向量由 rag 模块用 JdbcTemplate 写入与召回,
 * 因为 MyBatis-Plus 无法映射 pgvector 的 vector(1024) 类型。
 */
@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk>
        implements DocumentChunkService {
}
