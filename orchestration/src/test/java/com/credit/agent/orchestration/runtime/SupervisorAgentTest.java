package com.credit.agent.orchestration.runtime;

import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.CitationRef;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F4 汇聚单测: 冲突不投票、结论优先级、人工复核标记、引用与 token 合并。 */
class SupervisorAgentTest {

    private final SupervisorAgent supervisor =
            new SupervisorAgent(new IncomeConsistencyDetector(new BigDecimal("0.30")));

    @Test
    @DisplayName("F4 验收: 2 域说正常 + 收入/流水矛盾 → 结论 CONFLICT, 不按多数派投成通过")
    void conflictBeatsMajority() {
        SupervisionResult result = supervisor.aggregate(List.of(
                material("30000", DomainVerdict.NORMAL, 1L, "收入证明.pdf"),
                statement("20000", DomainVerdict.NORMAL, 2L, "流水.pdf"),
                policy(DomainVerdict.NORMAL, 3L, "2024年报.pdf")));

        assertEquals(SupervisionResult.CONFLICT, result.conclusion());
        assertEquals(1, result.conflicts().size());
        assertTrue(result.requiresHumanReview());
        assertEquals(3, result.citations().size());
        assertEquals(250, result.tokensUsed());
        assertTrue(result.citations().stream().allMatch(c -> c.chunkId() > 0));
    }

    @Test
    @DisplayName("全部正常 → PASS_SUGGEST, 但仍必须人工签批(§3.2 铁律)")
    void allNormalSuggestsPass() {
        SupervisionResult result = supervisor.aggregate(List.of(
                material("30000", DomainVerdict.NORMAL, 1L, "收入证明.pdf"),
                statement("30000", DomainVerdict.NORMAL, 2L, "流水.pdf")));

        assertEquals(SupervisionResult.PASS_SUGGEST, result.conclusion());
        assertFalse(result.hasConflict());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    @DisplayName("任一域要求补材料 → NEED_MORE_INFO, 优先级高于异常")
    void needMoreInfoWinsOverAbnormal() {
        SupervisionResult result = supervisor.aggregate(List.of(
                material("30000", DomainVerdict.NEED_MORE_INFO, 1L, "收入证明.pdf"),
                policy(DomainVerdict.ABNORMAL, 2L, "2024年报.pdf")));

        assertEquals(SupervisionResult.NEED_MORE_INFO, result.conclusion());
    }

    @Test
    @DisplayName("有异常但无冲突 → REJECT_SUGGEST(建议拒绝也必须人工签批)")
    void abnormalSuggestsReject() {
        SupervisionResult result = supervisor.aggregate(List.of(
                material("30000", DomainVerdict.NORMAL, 1L, "收入证明.pdf"),
                policy(DomainVerdict.ABNORMAL, 2L, "2024年报.pdf")));

        assertEquals(SupervisionResult.REJECT_SUGGEST, result.conclusion());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    @DisplayName("没有任何域结论 → NEED_MORE_INFO, 绝不默认通过")
    void emptyAnswersNeverPass() {
        assertEquals(SupervisionResult.NEED_MORE_INFO, supervisor.aggregate(List.of()).conclusion());
        assertEquals(SupervisionResult.NEED_MORE_INFO, supervisor.aggregate(null).conclusion());
    }

    @Test
    @DisplayName("引用去重合并: 同一 chunk 只保留一条")
    void citationsAreDistinct() {
        SupervisionResult result = supervisor.aggregate(List.of(
                material("30000", DomainVerdict.NORMAL, 7L, "同一页.pdf"),
                policy(DomainVerdict.NORMAL, 7L, "同一页.pdf")));

        assertEquals(1, result.citations().size());
        assertEquals("[同一页.pdf p.1]", result.citations().get(0).text());
    }

    private static DomainAnswer material(String income, DomainVerdict verdict, long chunkId, String docName) {
        return new DomainAnswer(AgentRole.MATERIAL, "材料域结论",
                List.of(new CitationRef(chunkId, docName, 1, 1)), 100, verdict,
                Map.of(DomainAnswer.FACT_INCOME_MONTHLY, new BigDecimal(income)));
    }

    private static DomainAnswer statement(String monthlyAvg, DomainVerdict verdict, long chunkId, String docName) {
        return new DomainAnswer(AgentRole.STATEMENT, "流水域结论",
                List.of(new CitationRef(chunkId, docName, 1, 1)), 50, verdict,
                Map.of(DomainAnswer.FACT_STATEMENT_MONTHLY_AVG, new BigDecimal(monthlyAvg)));
    }

    private static DomainAnswer policy(DomainVerdict verdict, long chunkId, String docName) {
        return new DomainAnswer(AgentRole.POLICY, "制度域结论",
                List.of(new CitationRef(chunkId, docName, 1, 1)), 100, verdict, Map.of());
    }
}
