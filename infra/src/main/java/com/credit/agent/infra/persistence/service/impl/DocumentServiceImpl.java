package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.mapper.DocumentMapper;
import com.credit.agent.infra.persistence.service.DocumentService;
import org.springframework.stereotype.Service;

/**
 * 材料/政策文档持久化实现。
 * 业务含义: 重传时新增 version+1 记录并置旧记录 isCurrent=false 的两步操作
 * 由调用方在明确事务边界内完成, 本类不自行开启事务。
 */
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
}
