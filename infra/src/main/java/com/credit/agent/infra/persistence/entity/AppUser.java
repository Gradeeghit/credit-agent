package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户(申请人)实体, 对应表 app_user。
 * 业务含义: 信贷申请的提交主体; 手机号只在 I1 做后 4 位身份校验,
 * 任何响应都必须脱敏成 138****5678, 本实体不承担脱敏(由 api 层输出时处理)。
 */
@Data
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户姓名 */
    private String name;

    /** 身份证号, 库内唯一, 属于敏感字段 */
    private String idCardNo;

    /** 手机号, 禁止明文返回前端 */
    private String phone;

    /** 建档时间, 由数据库默认值 / 自动填充写入 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
