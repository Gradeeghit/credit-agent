package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 申请单 Mapper。
 * 业务含义: 申请单状态机的读写入口; 状态迁移以 PRD §3.1 为唯一权威。
 */
@Mapper
public interface LoanApplicationMapper extends BaseMapper<LoanApplication> {
}
