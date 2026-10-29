package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.ApprovalStep;

/**
 * 审批步骤持久化服务。
 * 业务含义: 每步执行明细的追加写入; 重复 (traceId, stepIndex) 由唯一约束拦截, 服务层不吞异常。
 */
public interface ApprovalStepService extends IService<ApprovalStep> {
}
