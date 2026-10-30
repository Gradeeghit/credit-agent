package com.credit.agent.orchestration.application.security;

import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 角色解析单测: 大小写与空格容错、非法值拒绝、禁止显式传 ANONYMOUS。 */
class UserRoleTest {

    @Test
    @DisplayName("角色解析忽略大小写与首尾空格")
    void parseShouldTrimAndIgnoreCase() {
        assertEquals(UserRole.CHECKER, UserRole.from(" checker "));
        assertEquals(UserRole.APPROVER, UserRole.from("Approver"));
    }

    @Test
    @DisplayName("未知角色返回 400, 不静默降级")
    void rejectUnknownRole() {
        BizException ex = assertThrows(BizException.class, () -> UserRole.from("ADMIN"));

        assertEquals(400, ex.getStatus());
        assertEquals("USER_ROLE_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("空角色与显式 ANONYMOUS 都返回 400")
    void rejectBlankAndAnonymous() {
        assertEquals(400, assertThrows(BizException.class, () -> UserRole.from(" ")).getStatus());
        assertEquals("USER_ROLE_INVALID",
                assertThrows(BizException.class, () -> UserRole.from("ANONYMOUS")).getCode());
    }
}
