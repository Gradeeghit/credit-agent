package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.mapper.ApprovalTraceMapper;
import com.credit.agent.infra.persistence.service.ApprovalTraceService;
import org.springframework.stereotype.Service;

/**
 * 审批追踪持久化实现。
 * 业务含义: 支撑一次审批的开始/结束落库与按 trace_id 回放, 成本聚合查询请另写 XML。
 */
@Service
public class ApprovalTraceServiceImpl extends ServiceImpl<ApprovalTraceMapper, ApprovalTrace>
        implements ApprovalTraceService {
}
