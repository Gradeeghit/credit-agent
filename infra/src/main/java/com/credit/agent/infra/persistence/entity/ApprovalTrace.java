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
 * 审批追踪实体, 对应表 approval_trace。
 * 业务含义: 一次审批的汇总记录, trace_id 是全链路(HTTP→Agent→LLM→Tool→DB)串联的唯一键;
 * 同时承载成本聚合(totalTokens/totalCost)与失败归因(failureReason)。
 */
@Data
@TableName("approval_trace")
public class ApprovalTrace {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** 失败原因: 工具调用超时 */
    public static final String FAIL_TOOL_TIMEOUT = "TOOL_TIMEOUT";
    /** 失败原因: 工具参数不合法 */
    public static final String FAIL_TOOL_PARAM_INVALID = "TOOL_PARAM_INVALID";
    /** 失败原因: 大模型调用超时 */
    public static final String FAIL_LLM_TIMEOUT = "LLM_TIMEOUT";
    /** 失败原因: 超出 token 预算 */
    public static final String FAIL_BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    /** 失败原因: 超出步数上限 */
    public static final String FAIL_MAX_STEPS = "MAX_STEPS";
    /** 失败原因: 工具不存在 */
    public static final String FAIL_TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    /** 失败原因: 检索降级 */
    public static final String FAIL_RETRIEVAL_DEGRADED = "RETRIEVAL_DEGRADED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全链路追踪 ID, 库内唯一 */
    private String traceId;

    /** 所属申请单 ID, 关联 loan_application.id */
    private Long applicationId;

    /** 执行状态, 取值 RUNNING / COMPLETED / FAILED */
    private String status;

    /** 结论冗余汇总, 与 approval_report.conclusion 同枚举 */
    private String conclusion;

    /** 置信度, 四舍五入保留 4 位小数 */
    private BigDecimal confidence;

    /** 全链路耗时(毫秒) */
    private Long totalLatencyMs;

    /** 模型版本标识, 用于回放时对齐 */
    private String modelVersion;

    /** 提示词版本标识, 用于回放时对齐 */
    private String promptVersion;

    /** 本次审批消耗的总 token 数 */
    private Long totalTokens;

    /** 本次审批的累计成本(元), 精度 6 位小数 */
    private BigDecimal totalCost;

    /** 是否需要人工复核; 存在实质分歧时为 true, 签批意见必填 */
    private Boolean requiresHumanReview;

    /** 失败原因枚举值, 成功时为 null */
    private String failureReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
