package com.credit.agent.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F3 分域隔离单测: 每个域只允许装填自己那一槽证据。 */
class DomainBriefTest {

    private static final DomainEvidence MATERIAL_EVIDENCE =
            new DomainEvidence(1L, 11L, "身份证.pdf", 1, 1, "姓名张三");
    private static final DomainEvidence CREDIT_EVIDENCE =
            new DomainEvidence(2L, 12L, "征信报告.pdf", 2, 3, "近半年逾期 2 次");
    private static final DomainEvidence STATEMENT_EVIDENCE =
            new DomainEvidence(3L, 13L, "流水.pdf", 4, 5, "月均入账 2 万元");
    private static final DomainEvidence POLICY_EVIDENCE =
            new DomainEvidence(4L, 14L, "2024年报.pdf", 12, 13, "逾期 3 次以上不予准入");

    @Test
    @DisplayName("材料域: 只有 materials 非空, 其余三域必须为空")
    void materialBriefOnlyFillsItsOwnSlot() {
        DomainBrief brief = DomainBrief.forMaterial(9L, List.of(MATERIAL_EVIDENCE));

        assertEquals(AgentRole.MATERIAL, brief.role());
        assertEquals(9L, brief.applicationId());
        assertEquals(1, brief.materials().size());
        assertTrue(brief.creditReports().isEmpty());
        assertTrue(brief.statements().isEmpty());
        assertTrue(brief.policyChunks().isEmpty());
        assertEquals(1, brief.evidenceCount());
    }

    @Test
    @DisplayName("征信域: 只有 creditReports 非空")
    void creditBriefOnlyFillsItsOwnSlot() {
        DomainBrief brief = DomainBrief.forCredit(9L, List.of(CREDIT_EVIDENCE));

        assertEquals(1, brief.creditReports().size());
        assertTrue(brief.materials().isEmpty());
        assertTrue(brief.statements().isEmpty());
        assertTrue(brief.policyChunks().isEmpty());
        assertEquals(List.of(CREDIT_EVIDENCE), brief.evidence());
    }

    @Test
    @DisplayName("流水域: 只有 statements 非空")
    void statementBriefOnlyFillsItsOwnSlot() {
        DomainBrief brief = DomainBrief.forStatement(9L, List.of(STATEMENT_EVIDENCE));

        assertEquals(1, brief.statements().size());
        assertTrue(brief.materials().isEmpty());
        assertTrue(brief.creditReports().isEmpty());
        assertTrue(brief.policyChunks().isEmpty());
    }

    @Test
    @DisplayName("制度域: 只有 policyChunks 非空, 且不挂申请单")
    void policyBriefHasNoApplication() {
        DomainBrief brief = DomainBrief.forPolicy(List.of(POLICY_EVIDENCE));

        assertEquals(1, brief.policyChunks().size());
        assertTrue(brief.materials().isEmpty());
        assertTrue(brief.creditReports().isEmpty());
        assertTrue(brief.statements().isEmpty());
        assertEquals(null, brief.applicationId());
    }

    @Test
    @DisplayName("材料/征信/流水域缺申请单直接拒绝; 空证据允许但计数为 0")
    void rejectsMissingApplicationAndAllowsEmptyEvidence() {
        assertThrows(IllegalArgumentException.class, () -> DomainBrief.forMaterial(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> DomainBrief.forCredit(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> DomainBrief.forStatement(null, List.of()));

        DomainBrief empty = DomainBrief.forPolicy(null);
        assertEquals(0, empty.evidenceCount());
        assertTrue(empty.evidence().isEmpty());
    }
}
