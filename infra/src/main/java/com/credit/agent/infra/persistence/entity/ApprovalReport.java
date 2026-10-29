package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.credit.agent.infra.persistence.handler.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 初审报告实体, 对应表 approval_report。
 * 业务含义: 面向信审员的六节结构报告; 生成即锁定(locked=true), 签批时按
 * (applicationId, version) 定位, 版本不匹配必须返回 409; 旧版本只读保留。
 * 注意: 规则引擎算出的金额也存在 contentJson 里, 不另建表。
 */
@Data
@TableName(value = "approval_report", autoResultMap = true)
public class ApprovalReport {

    /** 结论: 建议通过 */
    public static final String CONCLUSION_PASS_SUGGEST = "PASS_SUGGEST";
    /** 结论: 建议拒绝 */
    public static final String CONCLUSION_REJECT_SUGGEST = "REJECT_SUGGEST";
    /** 结论: 需补充材料 */
    public static final String CONCLUSION_NEED_MORE_INFO = "NEED_MORE_INFO";
    /** 结论: 跨材料信息冲突, 必须转人工 */
    public static final String CONCLUSION_CONFLICT = "CONFLICT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属申请单 ID, 关联 loan_application.id */
    private Long applicationId;

    /** 生成该报告的审批追踪 ID */
    private String traceId;

    /** 报告版本号, 首次为 1, NEED_MORE_INFO 重跑生成 N+1 */
    private Integer version;

    /** 结论, 取值 PASS_SUGGEST/REJECT_SUGGEST/NEED_MORE_INFO/CONFLICT */
    private String conclusion;

    /** 六节结构报告正文 JSONB 原文, 由写入方负责序列化 */
    @TableField(value = "content_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String contentJson;

    /** 是否需要人工复核; 涉及放款/拒绝/冲突/闸门命中时必须为 true */
    private Boolean requiresHumanReview;

    /** 是否已锁定; true 时任何修改请求返回 409 */
    private Boolean locked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
