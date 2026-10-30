package com.credit.agent.orchestration.application.security;

import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 身份单测: 角色断言、敏感字段可见性、匿名身份无权限。 */
class CurrentUserTest {

    @Test
    @DisplayName("APPROVER 通过角色断言且可见明文敏感字段")
    void approverShouldPassAndSeeSensitive() {
        CurrentUser approver = new CurrentUser(1L, UserRole.APPROVER);

        assertDoesNotThrow(() -> approver.requireAny(UserRole.APPROVER));
        assertTrue(approver.canViewSensitiveFields());
        assertTrue(approver.isApprover());
    }

    @Test
    @DisplayName("CHECKER 改产品参数时抛 403, 且看不到明文敏感字段")
    void checkerShouldBeForbidden() {
        CurrentUser checker = new CurrentUser(2L, UserRole.CHECKER);

        BizException ex = assertThrows(BizException.class, () -> checker.requireAny(UserRole.APPROVER));

        assertEquals(403, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getCode());
        assertFalse(checker.canViewSensitiveFields());
    }

    @Test
    @DisplayName("匿名身份任何角色断言都失败")
    void anonymousShouldHaveNoPermission() {
        CurrentUser anonymous = CurrentUser.anonymous();

        assertEquals(UserRole.ANONYMOUS, anonymous.role());
        assertThrows(BizException.class, () -> anonymous.requireAny(UserRole.CUSTOMER));
    }
}
