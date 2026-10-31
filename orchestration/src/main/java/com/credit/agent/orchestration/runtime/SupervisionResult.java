package com.credit.agent.orchestration.runtime;

import java.util.List;

/**
 * 汇聚结果(F4), 是报告 conclusion 与 requires_human_review 的直接来源。
 * 业务含义: 四种结论与 approval_report.conclusion 的 CHECK 取值一致;
 * 只要存在冲突, 结论就是 CONFLICT 而不是"按多数派决定"——不投票是硬规矩。
 *
 * @param conclusion           PASS_SUGGEST / REJECT_SUGGEST / NEED_MORE_INFO / CONFLICT
 * @param conflicts            跨域冲突清单, 直接进报告
 * @param citations            各域引用去重合并
 * @param requiresHumanReview  §3.2: 任何结论都必须人工签批
 * @param tokensUsed           各域 token 之和, 供 F2 预算与 H3 成本
 */
public record SupervisionResult(
        String conclusion,
        List<DomainConflict> conflicts,
        List<String> citations,
        boolean requiresHumanReview,
        long tokensUsed) {

    public static final String PASS_SUGGEST = "PASS_SUGGEST";
    public static final String REJECT_SUGGEST = "REJECT_SUGGEST";
    public static final String NEED_MORE_INFO = "NEED_MORE_INFO";
    public static final String CONFLICT = "CONFLICT";

    public SupervisionResult {
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /** 是否存在跨域冲突。 */
    public boolean hasConflict() {
        return !conflicts.isEmpty();
    }
}
