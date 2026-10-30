package com.credit.agent.orchestration.application.security;

import com.credit.agent.orchestration.application.exception.BizException;

import java.util.Locale;

/**
 * 角色枚举(PRD §2)。
 * 业务含义: 客户只能看自己的申请状态, 信审员可受理与生成报告, 主管才有签批与改产品参数的权力;
 * ANONYMOUS 只用于开发阶段未传身份的场景, 不代表任何真实角色, 不得出现在验收结论里。
 */
public enum UserRole {

    /** 客户: 提交申请与材料, 只能查自己申请的状态 */
    CUSTOMER,

    /** 信审员: 受理申请、补传材料、发起初审、查看报告 */
    CHECKER,

    /** 主管: 复核并签批初审报告、维护产品参数 */
    APPROVER,

    /** 开发态占位身份: 无任何权限, 仅用于未开启身份校验时的兜底 */
    ANONYMOUS;

    /**
     * 把请求头里的角色文本解析成枚举。
     * 业务含义: 角色是权限判定的唯一依据, 非法值必须 400 拒绝而不是静默降级为最低权限。
     */
    public static UserRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("USER_ROLE_REQUIRED", "角色不能为空");
        }
        try {
            UserRole role = valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (role == ANONYMOUS) {
                throw BizException.badRequest("USER_ROLE_INVALID", "不允许显式传入 ANONYMOUS 角色");
            }
            return role;
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("USER_ROLE_INVALID",
                    "角色只能是 CUSTOMER / CHECKER / APPROVER, 实际为: " + raw);
        }
    }
}
