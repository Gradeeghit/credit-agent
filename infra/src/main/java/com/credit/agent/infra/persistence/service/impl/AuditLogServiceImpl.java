package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.AuditLog;
import com.credit.agent.infra.persistence.mapper.AuditLogMapper;
import com.credit.agent.infra.persistence.service.AuditLogService;
import org.springframework.stereotype.Service;

/**
 * 审计日志持久化实现。
 * 业务含义: 审计只追加; 任何"补写/订正"的诉求都应该新增一条记录, 而不是改旧记录。
 */
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {
}
