package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 初审报告 Mapper。
 * 业务含义: 报告版本写入与签批时的版本定位; 签批必须按 (applicationId, version) 命中, 未命中即 409。
 */
@Mapper
public interface ApprovalReportMapper extends BaseMapper<ApprovalReport> {
}
