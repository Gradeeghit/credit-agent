package com.credit.agent.api.web;

import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 开发阶段的身份解析器: 从请求头取操作人, 替代尚未实现的 I2/I3。
 * 业务含义: 只有声明了 {@link CurrentUser} 参数的接口才会走这里, 其余接口不受影响;
 * 身份缺失时按配置决定是 401 拒绝(identity-required=true)还是降级为匿名身份。
 * 后续接入 Spring Security 时, 只需替换本类的实现, Controller 签名不用改。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** 操作人主键头, 客户对应 app_user.id, 员工对应 sys_user.id */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 角色头, 取值 CUSTOMER / CHECKER / APPROVER */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private final boolean identityRequired;

    public CurrentUserArgumentResolver(boolean identityRequired) {
        this.identityRequired = identityRequired;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String rawId = webRequest.getHeader(HEADER_USER_ID);
        String rawRole = webRequest.getHeader(HEADER_USER_ROLE);
        if (rawId == null || rawId.isBlank() || rawRole == null || rawRole.isBlank()) {
            if (identityRequired) {
                throw BizException.unauthorized("USER_IDENTITY_REQUIRED",
                        "缺少身份请求头 " + HEADER_USER_ID + " 与 " + HEADER_USER_ROLE);
            }
            return CurrentUser.anonymous();
        }
        return new CurrentUser(parseUserId(rawId), UserRole.from(rawRole));
    }

    /** 身份主键必须是正整数: 0 与负数一律视为非法, 避免误命中不存在的 id 却放过校验。 */
    private static Long parseUserId(String raw) {
        try {
            long id = Long.parseLong(raw.trim());
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw BizException.badRequest("USER_ID_INVALID", HEADER_USER_ID + " 必须是正整数");
        }
    }
}
