package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.Document;

/**
 * 材料/政策文档持久化服务。
 * 业务含义: 材料上传、解析状态回写、重传换版(isCurrent 切换)的落库能力。
 */
public interface DocumentService extends IService<Document> {
}
