package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体, 对应表 audit_log(H6)。
 * 业务含义: 记录"谁在什么时候对哪张单子做了什么, 之前是什么状态, 之后是什么状态"。
 * 只追加不修改 —— 审计的价值在于不可篡改, 因此这里没有更新与删除的入口。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    /** 动作: 签批通过 */
    public static final String ACTION_APPROVE = "APPROVE";
    /** 动作: 签批驳回 */
    public static final String ACTION_REJECT = "REJECT";
    /** 动作: 要求补充材料 */
    public static final String ACTION_NEED_MORE_INFO = "NEED_MORE_INFO";

    /** 目标: 申请单 */
    public static final String TARGET_APPLICATION = "APPLICATION";

    /** 结果: 成功 */
    public static final String OUTCOME_SUCCESS = "SUCCESS";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全链路追踪 ID, 与同一请求的 trace 一致, 便于把"人的动作"与"AI 分析"串起来 */
    private String traceId;

    /** 操作人主键: 客户是 app_user.id, 内部员工是 sys_user.id */
    private Long operatorId;

    /** 操作人角色: CUSTOMER / CHECKER / APPROVER */
    private String operatorRole;

    /** 动作, 取值见本类 ACTION_* 与 DDL 的 ck_audit_action */
    private String action;

    /** 目标类型, 当前只有 APPLICATION */
    private String targetType;

    /** 目标主键 */
    private Long targetId;

    /** 操作前快照: 本实现写 status / version 文本, 不存整份业务对象 */
    private String beforeJson;

    /** 操作后快照 */
    private String afterJson;

    /** 操作说明或签批意见原文 */
    private String detail;

    /** 结果: SUCCESS / DENIED / FAILED */
    private String outcome;

    /** 发生时间, 由服务端统一填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
