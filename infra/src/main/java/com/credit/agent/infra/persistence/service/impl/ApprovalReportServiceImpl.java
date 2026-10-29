package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.mapper.ApprovalReportMapper;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import org.springframework.stereotype.Service;

/**
 * 初审报告持久化实现。
 * 业务含义: 报告按 (applicationId, version) 保持只读历史; 锁定与 409 冲突判定在编排层, 不在持久层。
 */
@Service
public class ApprovalReportServiceImpl extends ServiceImpl<ApprovalReportMapper, ApprovalReport>
        implements ApprovalReportService {
}
