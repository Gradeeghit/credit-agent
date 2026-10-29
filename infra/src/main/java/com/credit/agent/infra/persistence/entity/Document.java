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
 * 材料/政策文档实体, 对应表 document。
 * 业务含义: 客户提交的业务材料与制度政策文档共用一张表, 靠 docType 区分;
 * 重传不覆盖旧记录, 而是新增 version+1 并把旧记录 isCurrent 置 false。
 */
@Data
@TableName(value = "document", autoResultMap = true)
public class Document {

    public static final String DOC_TYPE_ID_CARD = "ID_CARD";
    public static final String DOC_TYPE_INCOME_CERT = "INCOME_CERT";
    public static final String DOC_TYPE_CREDIT_REPORT = "CREDIT_REPORT";
    public static final String DOC_TYPE_BANK_STATEMENT = "BANK_STATEMENT";
    public static final String DOC_TYPE_LOAN_PURPOSE = "LOAN_PURPOSE";
    public static final String DOC_TYPE_POLICY_DOC = "POLICY_DOC";

    public static final String PARSE_PENDING = "PENDING";
    public static final String PARSE_PARSED = "PARSED";
    public static final String PARSE_FAILED = "FAILED";
    /** 影像件无文本层, 解析不出文字时置此状态 */
    public static final String PARSE_UNSUPPORTED = "UNSUPPORTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属申请单 ID; docType 为 POLICY_DOC 时必为 null */
    private Long applicationId;

    /** 材料类型, 取值 ID_CARD/INCOME_CERT/CREDIT_REPORT/BANK_STATEMENT/LOAN_PURPOSE/POLICY_DOC */
    private String docType;

    /** 文件存放路径 */
    private String filePath;

    /** 版本号, 首次为 1, 每次重传 +1 */
    private Integer version;

    /** 是否为当前有效版本; false 表示已被重传版本替代 */
    @TableField("is_current")
    private Boolean isCurrent;

    /** 解析状态, 取值 PENDING/PARSED/FAILED/UNSUPPORTED */
    private String parseStatus;

    /** 解析失败原因, 成功时为 null */
    private String parseError;

    /** 扩展元数据, JSONB 原文, 由写入方负责序列化 */
    @TableField(value = "metadata", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
