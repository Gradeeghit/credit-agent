package com.credit.agent.orchestration.application;

import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.orchestration.application.exception.BizException;

import java.util.Locale;

/**
 * 签批动作规则(PRD §3.1 状态迁移 + §3.2 人工复核语义 + F7)。
 * 业务含义: 把"主管点了哪个按钮"确定性地翻译成"申请单去哪个状态"和"意见是否必须写"。
 * 三个动作与三个目标状态一一对应, 不存在第四种组合; 本类是纯函数, 不碰库也不碰模型, 可脱容器单测。
 */
public final class ApprovalDecisionRules {

    /** 需人工复核的报告, 通过意见的最小字数(PRD §3.2: 长度 ≥ 10 字) */
    public static final int MIN_REVIEW_COMMENT = 10;

    private ApprovalDecisionRules() {
    }

    /**
     * 签批动作。
     * 业务含义: 动作决定目标状态; APPROVE 要求报告无实质分歧时才允许只点按钮不写意见,
     * REJECT / NEED_MORE_INFO 一律必须写清理由, 否则后续无法向客户或审计解释。
     */
    public enum Decision {

        /** 通过: REPORT_READY → APPROVED(终态) */
        APPROVE(LoanApplication.STATUS_APPROVED),

        /** 驳回: REPORT_READY → REJECTED(终态) */
        REJECT(LoanApplication.STATUS_REJECTED),

        /** 要求补材料: REPORT_READY → NEED_MORE_INFO(非终态, 可补材料后重新受理) */
        NEED_MORE_INFO(LoanApplication.STATUS_NEED_MORE_INFO);

        private final String targetStatus;

        Decision(String targetStatus) {
            this.targetStatus = targetStatus;
        }

        /** 本动作会把申请单推进到的目标状态。 */
        public String targetStatus() {
            return targetStatus;
        }

        /** 是否无条件要求写意见: 驳回与要求补材料必须说明理由。 */
        public boolean requiresComment() {
            return this != APPROVE;
        }

        /**
         * 解析前端传来的动作文本。
         * 业务含义: 非法动作必须 400 拒绝而不是猜一个默认值 —— 签批是不可撤销的业务动作。
         */
        public static Decision from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw BizException.badRequest("DECISION_REQUIRED", "签批动作不能为空");
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("DECISION_INVALID",
                        "签批动作只能是 APPROVE / REJECT / NEED_MORE_INFO, 实际为: " + raw);
            }
        }
    }

    /**
     * 校验签批意见(F7 的可测点)。
     * 业务含义: 报告标了 requires_human_review=true 就代表"存在需要人判断的实质分歧",
     * 此时"无意见通过"等于把责任推回给系统, 必须拒绝; 未标记时允许一键通过。
     *
     * @param decision            签批动作
     * @param requiresHumanReview 报告是否需要人工复核
     * @param comment             签批意见原文, 可为 null
     * @throws BizException 400: 应填未填, 或需复核时字数不足
     */
    public static void validateComment(Decision decision, boolean requiresHumanReview, String comment) {
        String text = comment == null ? "" : comment.trim();
        if (decision.requiresComment() && text.isEmpty()) {
            throw BizException.badRequest("COMMENT_REQUIRED",
                    decision.name() + " 必须填写签批意见");
        }
        if (decision == Decision.APPROVE && requiresHumanReview && text.length() < MIN_REVIEW_COMMENT) {
            throw BizException.badRequest("COMMENT_TOO_SHORT",
                    "该报告存在需人工判断的分歧, 通过意见不得少于 " + MIN_REVIEW_COMMENT + " 字");
        }
    }

    /** 统一把意见规整为可入库文本: 去首尾空白, 空串归一为 null。 */
    public static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String text = comment.trim();
        return text.isEmpty() ? null : text;
    }
}
