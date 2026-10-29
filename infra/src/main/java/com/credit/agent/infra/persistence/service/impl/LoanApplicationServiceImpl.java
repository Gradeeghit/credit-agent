package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.mapper.LoanApplicationMapper;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import org.springframework.stereotype.Service;

/**
 * 申请单持久化实现。
 * 业务含义: 只提供 loan_application 的增删改查与状态字段更新,
 * 状态迁移是否合法由编排层按 PRD §3.1 判定, 不在持久层做隐式校验。
 */
@Service
public class LoanApplicationServiceImpl extends ServiceImpl<LoanApplicationMapper, LoanApplication>
        implements LoanApplicationService {
}
