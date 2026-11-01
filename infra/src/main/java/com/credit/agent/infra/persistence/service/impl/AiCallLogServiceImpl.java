package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.AiCallLog;
import com.credit.agent.infra.persistence.mapper.AiCallLogMapper;
import com.credit.agent.infra.persistence.service.AiCallLogService;
import org.springframework.stereotype.Service;

/**
 * LLM 调用明细持久化实现。
 * 业务含义: 纯明细存储, 不做聚合计算 —— 成本在写入前已由 domain 算好。
 */
@Service
public class AiCallLogServiceImpl extends ServiceImpl<AiCallLogMapper, AiCallLog>
        implements AiCallLogService {
}
