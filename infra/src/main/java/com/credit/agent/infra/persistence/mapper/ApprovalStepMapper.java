package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批步骤 Mapper。
 * 业务含义: 每步执行明细的追加写入; 只追加不修改, 供 Trace 回放使用。
 */
@Mapper
public interface ApprovalStepMapper extends BaseMapper<ApprovalStep> {
}
