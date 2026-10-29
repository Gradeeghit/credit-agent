package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.LoanApplication;

/**
 * 申请单持久化服务。
 * 业务含义: 申请单的读写与状态字段更新; 状态机合法性校验在编排层, 本服务不判断能否迁移。
 */
public interface LoanApplicationService extends IService<LoanApplication> {
}
