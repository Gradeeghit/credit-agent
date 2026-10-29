package com.credit.agent.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内部员工账号实体, 对应表 sys_user。
 * 业务含义: 信审员(CHECKER)/ 主管(APPROVER) 的登录账号;
 * 禁止存明文密码, 只存 BCrypt 摘要; 连续错 3 次锁 601 秒由登录逻辑维护。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 角色: 信审员 */
    public static final String ROLE_CHECKER = "CHECKER";

    /** 角色: 主管(签批人) */
    public static final String ROLE_APPROVER = "APPROVER";

    /** 状态: 正常 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态: 停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名, 库内唯一 */
    private String username;

    /** BCrypt 密码摘要, 长度 60 */
    private String passwordHash;

    /** 角色, 取值 CHECKER / APPROVER */
    private String role;

    /** 账号状态, 取值 ACTIVE / DISABLED */
    private String status;

    /** 连续登录失败次数, 登录成功或锁定期满后由登录逻辑清零 */
    private Integer failCount;

    /** 锁定截止时间; 非空且晚于当前时间表示锁定中 */
    private LocalDateTime lockedUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
