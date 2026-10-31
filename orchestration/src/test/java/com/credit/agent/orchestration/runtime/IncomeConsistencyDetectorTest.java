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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F4 冲突检测单测: 收入证明 3 万 vs 流水 2 万必须判冲突。 */
class IncomeConsistencyDetectorTest {

    private final IncomeConsistencyDetector detector =
            new IncomeConsistencyDetector(new BigDecimal("0.30"));

    @Test
    @DisplayName("F4 验收 case: 收入证明 3 万 / 流水月均 2 万 → 1 条 INCOME_INCONSISTENT 冲突")
    void incomeMismatchIsConflict() {
        List<DomainConflict> conflicts = detector.detect(List.of(
                material("30000.00", 1L, "收入证明.pdf"),
                statement("20000.00", 2L, "流水.pdf")));

        assertEquals(1, conflicts.size());
        DomainConflict conflict = conflicts.get(0);
        assertEquals(DomainConflict.CODE_INCOME_INCONSISTENT, conflict.code());
        assertTrue(conflict.summary().contains("30000.00"));
        assertTrue(conflict.summary().contains("20000.00"));
        // 偏差基数是收入证明: |30000-20000| / 30000 = 33.33%
        assertTrue(conflict.summary().contains("33.33%"));
        assertTrue(conflict.summary().contains("相对收入证明"));
        assertEquals(List.of("[收入证明.pdf p.1]", "[流水.pdf p.1]"), conflict.citations());
    }

    @Test
    @DisplayName("偏差 5% 不判冲突; 恰好等于 30% 阈值也不判(阈值是闭区间)")
    void withinToleranceIsNotConflict() {
        assertTrue(detector.detect(List.of(material("30000", 1L, "a"), statement("28500", 2L, "b"))).isEmpty());
        assertTrue(detector.detect(List.of(material("30000", 1L, "a"), statement("21000", 2L, "b"))).isEmpty());
    }

    @Test
    @DisplayName("某一域没有该数值: 不判冲突(缺证据走需补材料, 不是矛盾)")
    void missingFactIsNotConflict() {
        DomainAnswer materialWithoutFact = new DomainAnswer(AgentRole.MATERIAL, "正常",
                List.of(new CitationRef(1L, "a", 1, 1)), 10, DomainVerdict.NORMAL, Map.of());

        assertTrue(detector.detect(List.of(materialWithoutFact, statement("20000", 2L, "b"))).isEmpty());
        assertTrue(detector.detect(List.of(material("30000", 1L, "a"))).isEmpty());
    }

    @Test
    @DisplayName("收入为 0 或空列表: 不判冲突, 也不允许除零")
    void zeroIncomeIsSafe() {
        assertTrue(detector.detect(List.of(material("0", 1L, "a"), statement("20000", 2L, "b"))).isEmpty());
        assertTrue(detector.detect(List.of()).isEmpty());
        assertTrue(detector.detect(null).isEmpty());
    }

    @Test
    @DisplayName("阈值可配置: 放宽到 60% 后同一组数据不再冲突")
    void toleranceIsConfigurable() {
        IncomeConsistencyDetector loose = new IncomeConsistencyDetector(new BigDecimal("0.60"));

        assertTrue(loose.detect(List.of(material("30000", 1L, "a"), statement("20000", 2L, "b"))).isEmpty());
    }

    private static DomainAnswer material(String income, long chunkId, String docName) {
        return new DomainAnswer(AgentRole.MATERIAL, "收入证明已核验",
                List.of(new CitationRef(chunkId, docName, 1, 1)), 100,
                DomainVerdict.NORMAL, Map.of(DomainAnswer.FACT_INCOME_MONTHLY, new BigDecimal(income)));
    }

    private static DomainAnswer statement(String monthlyAvg, long chunkId, String docName) {
        return new DomainAnswer(AgentRole.STATEMENT, "流水已核验",
                List.of(new CitationRef(chunkId, docName, 1, 1)), 50,
                DomainVerdict.NORMAL,
                Map.of(DomainAnswer.FACT_STATEMENT_MONTHLY_AVG, new BigDecimal(monthlyAvg)));
    }
}
