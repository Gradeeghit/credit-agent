package com.credit.agent.orchestration.application;

import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.orchestration.application.ApprovalDecisionRules.Decision;
import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 签批规则单测: 动作→目标状态一一对应, 意见必填与字数门槛(PRD §3.1 / §3.2 / F7)。 */
class ApprovalDecisionRulesTest {

    @Test
    @DisplayName("三个动作各自映射到唯一目标状态")
    void actionsMapToTargetStatus() {
        assertEquals(LoanApplication.STATUS_APPROVED, Decision.APPROVE.targetStatus());
        assertEquals(LoanApplication.STATUS_REJECTED, Decision.REJECT.targetStatus());
        assertEquals(LoanApplication.STATUS_NEED_MORE_INFO, Decision.NEED_MORE_INFO.targetStatus());
    }

    @Test
    @DisplayName("驳回与要求补材料: 意见为空直接 400")
    void rejectAndNeedMoreInfoRequireComment() {
        assertEquals(400, assertThrows(BizException.class,
                () -> ApprovalDecisionRules.validateComment(Decision.REJECT, false, "  ")).getStatus());
        assertEquals(400, assertThrows(BizException.class,
                () -> ApprovalDecisionRules.validateComment(Decision.NEED_MORE_INFO, false, null)).getStatus());
    }

    @Test
    @DisplayName("需人工复核的报告: 通过意见不足 10 字 400, 恰好 10 字通过")
    void reviewFlagEnforcesTenChars() {
        BizException ex = assertThrows(BizException.class,
                () -> ApprovalDecisionRules.validateComment(Decision.APPROVE, true, "同意通过吧"));
        assertEquals(400, ex.getStatus());
        assertEquals("COMMENT_TOO_SHORT", ex.getCode());

        assertDoesNotThrow(() ->
                ApprovalDecisionRules.validateComment(Decision.APPROVE, true, "已核对流水与收入证明一致"));
    }

    @Test
    @DisplayName("无需复核的报告: 通过时意见可空")
    void plainApproveAllowsEmptyComment() {
        assertDoesNotThrow(() -> ApprovalDecisionRules.validateComment(Decision.APPROVE, false, null));
        assertDoesNotThrow(() -> ApprovalDecisionRules.validateComment(Decision.APPROVE, false, ""));
    }

    @Test
    @DisplayName("非法或空动作: 400, 不默认放行")
    void invalidActionIsRejected() {
        assertEquals(400, assertThrows(BizException.class,
                () -> Decision.from("PASS")).getStatus());
        assertEquals(400, assertThrows(BizException.class,
                () -> Decision.from(" ")).getStatus());
        assertEquals(Decision.NEED_MORE_INFO, Decision.from(" need_more_info "));
    }

    @Test
    @DisplayName("意见规整: 去空白, 空串归一为 null")
    void normalizeCommentTrims() {
        assertEquals("同意", ApprovalDecisionRules.normalizeComment("  同意 "));
        assertNull(ApprovalDecisionRules.normalizeComment("   "));
    }
}
