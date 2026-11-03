package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 调用计量实体, 对应表 ai_call_log(H3)。
 * 业务含义: 每次模型调用留一条明细, 按 trace_id 聚合即为单笔审批成本与调用次数;
 * cost 由 domain 的 TokenCost 按单价确定性算好再写入, 本实体不做任何运算。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全链路追踪 ID, 与 approval_trace.trace_id 关联 */
    private String traceId;

    /** 调用类型: CHAT / EMBEDDING, 决定用哪组单价计价 */
    private String kind;

    /** 模型标识, 取不到时写 unknown */
    private String model;

    /** 输入 token */
    private Long promptTokens;

    /** 输出 token */
    private Long completionTokens;

    /** 成本(元), 6 位小数; 单价未配置时为 null */
    private BigDecimal cost;

    /** 调用耗时(毫秒) */
    private Long latencyMs;

    /** 工具循环内的轮次, 从 1 起; 一次 ask 可能对应多条(每轮都计费), 只记最后一轮会低报成本 */
    private Integer roundNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
