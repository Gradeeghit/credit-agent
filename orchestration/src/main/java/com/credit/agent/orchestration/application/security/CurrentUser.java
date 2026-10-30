package com.credit.agent.orchestration.application.security;

import com.credit.agent.orchestration.application.exception.BizException;

/**
 * 当前请求的身份(A 阶段替代 I2/I3 的轻量方案)。
 * 业务含义: 承载"谁在操作"; 现阶段由前端通过请求头直传, 后续接入 Spring Security 时
 * 只需替换 api 层的解析器, 业务代码对身份的用法不变。
 *
 * @param id   操作人主键: CUSTOMER 对应 app_user.id, CHECKER/APPROVER 对应 sys_user.id
 * @param role 角色, 决定能做什么与能看到什么
 */
public record CurrentUser(Long id, UserRole role) {

    /** 开发态匿名身份: 无任何权限, 仅当关闭身份校验时使用。 */
    public static CurrentUser anonymous() {
        return new CurrentUser(0L, UserRole.ANONYMOUS);
    }

    /** 是否为主管。 */
    public boolean isApprover() {
        return role == UserRole.APPROVER;
    }

    /** 是否为信审员。 */
    public boolean isChecker() {
        return role == UserRole.CHECKER;
    }

    /** 是否为客户。 */
    public boolean isCustomer() {
        return role == UserRole.CUSTOMER;
    }

    /**
     * 是否能查看未脱敏的手机号与证件号(H5)。
     * 业务含义: 只有主管可见明文, 信审员看到 138****5678, 客户看不到他人信息。
     */
    public boolean canViewSensitiveFields() {
        return role == UserRole.APPROVER;
    }

    /**
     * 断言当前身份属于给定角色之一, 否则抛 403。
     * 业务含义: 越权不是 404, 必须明确返回"无权限", 便于审计与前端提示。
     */
    public void requireAny(UserRole... allowed) {
        for (UserRole candidate : allowed) {
            if (candidate == role) {
                return;
            }
        }
        throw BizException.forbidden("FORBIDDEN", "当前角色 " + role + " 无权执行该操作");
    }
}
