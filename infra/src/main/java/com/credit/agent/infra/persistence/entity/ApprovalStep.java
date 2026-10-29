package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批步骤实体, 对应表 approval_step。
 * 业务含义: 每一步执行明细, 只追加不修改, 支撑 Trace 回放与步数上限断言;
 * UNIQUE(trace_id, step_index) 是并发重复写入的最终防线。
 */
@Data
@TableName("approval_step")
public class ApprovalStep {

    public static final String AGENT_MATERIAL = "MATERIAL";
    public static final String AGENT_CREDIT = "CREDIT";
    public static final String AGENT_STATEMENT = "STATEMENT";
    public static final String AGENT_POLICY = "POLICY";
    public static final String AGENT_SUPERVISOR = "SUPERVISOR";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属审批追踪 ID, 关联 approval_trace.trace_id */
    private String traceId;

    /** 步骤序号, 从 1 开始, 与 traceId 组成唯一约束 */
    private Integer stepIndex;

    /** 执行该步的 Agent 角色, 取值 MATERIAL/CREDIT/STATEMENT/POLICY/SUPERVISOR */
    private String agentRole;

    /** 调用的工具名, 纯 LLM 推理步为 null */
    private String toolName;

    /** 输入摘要(截断后的可读文本), 用于回放 */
    private String inputSummary;

    /** 输出摘要(截断后的可读文本), 用于回放 */
    private String outputSummary;

    /** 本步耗时(毫秒) */
    private Long latencyMs;

    /** 本步失败原因枚举值, 成功时为 null */
    private String failureReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
