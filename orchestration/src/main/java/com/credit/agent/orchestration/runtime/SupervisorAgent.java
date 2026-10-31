package com.credit.agent.orchestration.runtime;

import com.credit.agent.agent.domain.CitationRef;
import com.credit.agent.agent.domain.DomainAnswer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 汇总 Agent(F4): 把各域结论收敛成一条初审结论。
 * 业务含义: 汇聚只做三件事 —— 合并引用、识别冲突、按固定优先级给结论;
 * **不对冲突投票**("两个域说 A、一个域说 B 就选 A"是错的), 有冲突一律 CONFLICT 转人工。
 * 本类不调用 LLM, 不计算金额, 结论完全由结构化判据决定, 可复现。
 */
@Service
public class SupervisorAgent {

    private final IncomeConsistencyDetector conflictDetector;

    public SupervisorAgent(IncomeConsistencyDetector conflictDetector) {
        this.conflictDetector = conflictDetector;
    }

    /**
     * 汇聚各域结论。
     * 业务含义: 优先级固定为「冲突 > 需补材料 > 异常(建议拒绝) > 通过建议」;
     * 没有任何域结论时按需补材料处理, 绝不默认通过。
     *
     * @param answers 各域结论
     * @return 结论、冲突清单、引用与人工复核标记
     */
    public SupervisionResult aggregate(List<DomainAnswer> answers) {
        List<DomainAnswer> safe = answers == null ? List.of()
                : answers.stream().filter(Objects::nonNull).toList();
        List<DomainConflict> conflicts = conflictDetector.detect(safe);
        String conclusion = decide(safe, conflicts);
        List<CitationRef> citations = safe.stream()
                .flatMap(a -> a.citations().stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        long tokens = safe.stream().mapToLong(DomainAnswer::tokensUsed).sum();
        return new SupervisionResult(conclusion, conflicts, citations, requiresHumanReview(conclusion), tokens);
    }

    /** 结论优先级: 冲突 > 需补 > 异常 > 通过; 无域结论视为需补。 */
    static String decide(List<DomainAnswer> answers, List<DomainConflict> conflicts) {
        if (conflicts != null && !conflicts.isEmpty()) {
            return SupervisionResult.CONFLICT;
        }
        if (answers.isEmpty()) {
            return SupervisionResult.NEED_MORE_INFO;
        }
        if (answers.stream().anyMatch(DomainAnswer::needsMoreInfo)) {
            return SupervisionResult.NEED_MORE_INFO;
        }
        if (answers.stream().anyMatch(DomainAnswer::isAbnormal)) {
            return SupervisionResult.REJECT_SUGGEST;
        }
        return SupervisionResult.PASS_SUGGEST;
    }

    /**
     * 人工复核标记(§3.2 铁律)。
     * 业务含义: 建议放款、建议拒绝、需补材料、CONFLICT 四种结论都必须主管签批,
     * 所以现阶段恒为 true; 等 §3.2 的"实质分歧"语义细化后再按结论区分。
     */
    static boolean requiresHumanReview(String conclusion) {
        return conclusion != null;
    }
}
