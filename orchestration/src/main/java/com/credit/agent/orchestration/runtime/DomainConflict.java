package com.credit.agent.orchestration.runtime;

import java.util.List;

/**
 * 跨材料域的信息冲突(F4)。
 * 业务含义: 冲突不是"谁对谁错", 而是"两个域说的不一致, 必须人来看";
 * 因此本类只描述冲突事实与双方引用, 绝不携带任何倾向性结论(不投票)。
 *
 * @param code     冲突类型码, 如 INCOME_INCONSISTENT
 * @param summary  人读摘要, 含两侧数值与偏差比例, 直接进报告
 * @param citations 双方证据的引用, 供审批人核对
 */
public record DomainConflict(String code, String summary, List<String> citations) {

    /** 收入证明与流水月均偏差过大 */
    public static final String CODE_INCOME_INCONSISTENT = "INCOME_INCONSISTENT";

    public DomainConflict {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
