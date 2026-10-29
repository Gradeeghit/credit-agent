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
 * 文档切片实体, 对应表 document_chunk。
 * 业务含义: 报告里每条引用 [文件名 p.12-13] 都必须能回链到本表某一行(靠 pageStart/pageEnd 渲染)。
 * 重要: embedding 列是 pgvector 的 vector(1024), MyBatis-Plus 不认该类型, 因此本实体刻意不映射它;
 * 向量的写入与检索必须走 rag 模块的 JdbcTemplate(CAST(? AS vector)), 不要在此处补字段。
 */
@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID, 关联 document.id */
    private Long docId;

    /** 切片正文 */
    private String content;

    /** 起始页码; 跨页时 pageEnd 大于 pageStart */
    private Integer pageStart;

    /** 结束页码 */
    private Integer pageEnd;

    /** 是否为当前有效切片; 材料作废后置 false, 检索只命中 true */
    @TableField("is_current")
    private Boolean isCurrent;

    /** 切片元数据 JSONB 原文, 约定含 docId/section/charStart */
    @TableField(value = "metadata", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
