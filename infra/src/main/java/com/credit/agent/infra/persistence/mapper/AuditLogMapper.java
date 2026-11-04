package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper。
 * 业务含义: 只提供追加与查询; 审计记录不提供更新/删除, 保证"发生过的事实"不可被改写。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
