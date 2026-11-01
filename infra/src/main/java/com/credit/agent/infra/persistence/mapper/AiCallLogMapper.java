package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * LLM 调用明细 Mapper。
 * 业务含义: 只做明细读写; 按 trace 聚合成本用 IService + QueryWrapper 即可, 复杂报表再写 XML。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
