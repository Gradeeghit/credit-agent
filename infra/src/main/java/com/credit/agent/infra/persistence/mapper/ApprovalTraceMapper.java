package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批追踪 Mapper。
 * 业务含义: 一次审批的汇总读写; 成本看板按 trace_id 聚合的复杂查询请写 XML, 不要硬塞 LambdaQuery。
 */
@Mapper
public interface ApprovalTraceMapper extends BaseMapper<ApprovalTrace> {
}
