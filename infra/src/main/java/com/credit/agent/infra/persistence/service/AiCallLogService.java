package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.AiCallLog;

/**
 * LLM 调用明细持久化服务(H3)。
 * 业务含义: 供 AiCallLogger 落库与 Trace 回放按 trace_id 聚合成本使用。
 */
public interface AiCallLogService extends IService<AiCallLog> {
}
