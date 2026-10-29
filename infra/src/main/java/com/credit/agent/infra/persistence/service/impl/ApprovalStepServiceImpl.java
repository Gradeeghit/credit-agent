package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.mapper.ApprovalStepMapper;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import org.springframework.stereotype.Service;

/**
 * 审批步骤持久化实现。
 * 业务含义: 每步明细只追加不修改; 重复 (traceId, stepIndex) 由库唯一约束抛异常, 本类不做吞异常处理。
 */
@Service
public class ApprovalStepServiceImpl extends ServiceImpl<ApprovalStepMapper, ApprovalStep>
        implements ApprovalStepService {
}
