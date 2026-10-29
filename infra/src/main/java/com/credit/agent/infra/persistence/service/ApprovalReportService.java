package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.ApprovalReport;

/**
 * 初审报告持久化服务。
 * 业务含义: 报告版本落库与只读查询; 锁定(locked)与版本冲突(409)的判断在编排层。
 */
public interface ApprovalReportService extends IService<ApprovalReport> {
}
