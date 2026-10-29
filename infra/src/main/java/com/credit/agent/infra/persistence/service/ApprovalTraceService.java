package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;

/**
 * 审批追踪持久化服务。
 * 业务含义: 一次审批的开始/结束落库, 供可观测与成本聚合按 trace_id 查询。
 */
public interface ApprovalTraceService extends IService<ApprovalTrace> {
}
