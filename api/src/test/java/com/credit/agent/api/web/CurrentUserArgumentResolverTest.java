package com.credit.agent.api.web;

import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 身份解析单测: 正常解析、缺头两种策略、非法角色、非法 id。 */
class CurrentUserArgumentResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final NativeWebRequest webRequest = new ServletWebRequest(request);

    @Test
    @DisplayName("请求头完整时解析出 id 与角色")
    void resolveFromHeaders() {
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ID, "42");
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ROLE, "approver");

        CurrentUser user = resolve(true);

        assertEquals(42L, user.id());
        assertEquals(UserRole.APPROVER, user.role());
    }

    @Test
    @DisplayName("缺少身份头且强制校验时返回 401")
    void missingHeaderShouldFail() {
        BizException ex = assertThrows(BizException.class, () -> resolve(true));

        assertEquals(401, ex.getStatus());
        assertEquals("USER_IDENTITY_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("关闭强制校验后缺头降级为匿名身份")
    void missingHeaderShouldFallbackToAnonymous() {
        CurrentUser user = resolve(false);

        assertEquals(UserRole.ANONYMOUS, user.role());
    }

    @Test
    @DisplayName("角色非法返回 400")
    void invalidRoleShouldFail() {
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ID, "42");
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ROLE, "ROOT");

        assertEquals(400, assertThrows(BizException.class, () -> resolve(true)).getStatus());
    }

    @Test
    @DisplayName("用户 id 非正整数返回 400")
    void invalidUserIdShouldFail() {
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ID, "-1");
        request.addHeader(CurrentUserArgumentResolver.HEADER_USER_ROLE, "CHECKER");

        assertEquals("USER_ID_INVALID", assertThrows(BizException.class, () -> resolve(true)).getCode());
    }

    private CurrentUser resolve(boolean identityRequired) {
        return new CurrentUserArgumentResolver(identityRequired)
                .resolveArgument(null, null, webRequest, null);
    }
}
