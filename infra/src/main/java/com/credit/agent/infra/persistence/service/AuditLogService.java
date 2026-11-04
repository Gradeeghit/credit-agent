package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.AuditLog;

/**
 * 审计日志持久化服务。
 * 业务含义: 只做追加; "写失败要不要中断业务"的判断留在编排层(结论: 不中断, 只告警)。
 */
public interface AuditLogService extends IService<AuditLog> {
}
