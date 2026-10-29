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
 * 申请单实体, 对应表 loan_application。
 * 业务含义: 审批状态机的载体; status 迁移以 PRD §3.1 为唯一权威, 终态(APPROVED/REJECTED)不可再迁移。
 * 幂等: UNIQUE(customer_id, client_request_id), 同一客户重复提交只落 1 条。
 */
@Data
@TableName("loan_application")
public class LoanApplication {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_REPORT_READY = "REPORT_READY";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NEED_MORE_INFO = "NEED_MORE_INFO";

    /** 收入口径: 月收入 */
    public static final String INCOME_TYPE_MONTHLY = "MONTHLY";

    /** 收入口径: 年流水 */
    public static final String INCOME_TYPE_YEARLY = "YEARLY";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对客申请编号, 库内唯一 */
    private String appNo;

    /** 申请人 ID, 关联 app_user.id */
    private Long customerId;

    /** 产品 ID, 关联 product.id */
    private Long productId;

    /** 客户端幂等键, 与 customerId 组成唯一约束 */
    private String clientRequestId;

    /** 申请金额(元), 必须大于 0, 统一 BigDecimal */
    private BigDecimal amount;

    /** 申请期限(月), 必须大于 0 */
    private Integer termMonths;

    /** 贷款用途类型 */
    private String purpose;

    /** 用途说明, 敏感词闸门的输入 */
    private String purposeDesc;

    /** 月收入 或 年流水, 与 incomeType 配套 */
    private BigDecimal income;

    /** 收入口径, 取值 MONTHLY / YEARLY */
    private String incomeType;

    /** 状态, 取值 SUBMITTED/PROCESSING/REPORT_READY/APPROVED/REJECTED/NEED_MORE_INFO */
    private String status;

    /** 受理人 sys_user.id; 客户自提交时为 null */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间, 每次状态迁移或字段修改时由自动填充刷新 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
