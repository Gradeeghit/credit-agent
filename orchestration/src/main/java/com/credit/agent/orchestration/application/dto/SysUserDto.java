package com.credit.agent.orchestration.application.dto;

import java.time.LocalDateTime;

/**
 * 员工账号对外契约(I2/I3)。
 * 业务含义: 信审员与主管账号维护; 响应中永远不含 passwordHash, 密码设置走独立接口。
 */
public final class SysUserDto {

    private SysUserDto() {
    }

    /** 新建/修改入参; 不含密码字段, 密码由 I2 登录模块单独设置。 */
    public record SaveRequest(String username, String role, String status) {
    }

    /** 账号视图, 不含密码摘要。 */
    public record View(
            Long id,
            String username,
            String role,
            String status,
            Integer failCount,
            LocalDateTime lockedUntil,
            LocalDateTime createdAt) {
    }
}
