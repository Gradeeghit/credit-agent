package com.credit.agent.orchestration.report;

import com.credit.agent.agent.domain.CitationRef;
import com.credit.agent.orchestration.runtime.DomainConflict;
import com.credit.agent.orchestration.runtime.FailureReason;
import com.credit.agent.orchestration.runtime.SupervisionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G1 六节结构单测: 六节非空、冲突进风险点、置信度查表、引用带 chunkId。 */
class ReportContentBuilderTest {

    private final ReportContentBuilder builder = new ReportContentBuilder();

    @Test
    @DisplayName("G1 验收: 冲突场景下六节均非空, 且引用都带可回链的 chunkId")
    void conflictReportHasAllSixSections() {
        SupervisionResult supervision = new SupervisionResult(SupervisionResult.CONFLICT,
                List.of(new DomainConflict(DomainConflict.CODE_INCOME_INCONSISTENT,
                        "收入证明月收入 30000.00 与流水月均 20000.00 偏差 33.33%", List.of("[流水.pdf p.1]"))),
                List.of(new CitationRef(11L, "收入证明.pdf", 2, 2), new CitationRef(12L, "流水.pdf", 4, 5)),
                true, 250);

        ReportContent content = builder.build(supervision, rules(), List.of());

        assertFalse(content.conclusion().isBlank());
        assertEquals(2, content.citations().size());
        assertTrue(content.citations().stream().allMatch(c -> c.chunkId() > 0));
        assertEquals(1, content.risks().size());
        assertEquals(DomainConflict.CODE_INCOME_INCONSISTENT, content.risks().get(0).code());
        assertEquals(RiskItem.LEVEL_HIGH, content.risks().get(0).level());
        assertEquals(new BigDecimal("0.30"), content.confidence());
        assertEquals(List.of("无"), content.pendingItems());
        assertTrue(content.isConflict());
    }

    @Test
    @DisplayName("无冲突且建议通过: 风险点用 NONE 占位, 置信度 0.90, 待补填「无」")
    void passReportUsesPlaceholders() {
        ReportContent content = builder.build(
                new SupervisionResult(SupervisionResult.PASS_SUGGEST, List.of(),
                        List.of(new CitationRef(1L, "材料.pdf", 1, 1)), true, 100),
                rules(), null);

        assertTrue(content.risks().get(0).isPlaceholder());
        assertEquals(RiskItem.CODE_NONE, content.risks().get(0).code());
        assertEquals(new BigDecimal("0.90"), content.confidence());
        assertEquals(List.of("无"), content.pendingItems());
    }

    @Test
    @DisplayName("建议拒绝但无冲突: 风险点补一条域异常, 置信度 0.60")
    void rejectWithoutConflictStillHasRisk() {
        ReportContent content = builder.build(
                new SupervisionResult(SupervisionResult.REJECT_SUGGEST, List.of(),
                        List.of(new CitationRef(1L, "征信.pdf", 1, 1)), true, 100),
                rules(), List.of());

        assertEquals(RiskItem.CODE_DOMAIN_ABNORMAL, content.risks().get(0).code());
        assertEquals(new BigDecimal("0.60"), content.confidence());
    }

    @Test
    @DisplayName("需补材料: 传入待补清单原样保留; 未传时给占位而不是空白")
    void needMoreInfoPendingItems() {
        SupervisionResult supervision = new SupervisionResult(SupervisionResult.NEED_MORE_INFO, List.of(),
                List.of(new CitationRef(1L, "材料.pdf", 1, 1)), true, 100);

        assertEquals(List.of("补交近 6 个月流水"),
                builder.build(supervision, rules(), List.of("补交近 6 个月流水")).pendingItems());
        assertTrue(builder.build(supervision, rules(), List.of()).pendingItems().get(0).contains("待补"));
        assertEquals(new BigDecimal("0.50"), builder.build(supervision, rules(), List.of()).confidence());
    }

    @Test
    @DisplayName("降级前结论是建议拒绝: 即使被覆盖成 NEED_MORE_INFO, 域异常风险仍要保留")
    void degradedButAbnormalStillKeepsDomainRisk() {
        ReportContent content = builder.build(
                new SupervisionResult(SupervisionResult.NEED_MORE_INFO, List.of(),
                        List.of(new CitationRef(1L, "征信.pdf", 1, 1)), true, 100),
                rules(), List.of(), FailureReason.BUDGET_EXCEEDED,
                SupervisionResult.REJECT_SUGGEST);

        assertEquals(SupervisionResult.NEED_MORE_INFO, content.conclusion());
        assertEquals(2, content.risks().size());
        assertEquals(RiskItem.CODE_DOMAIN_ABNORMAL, content.risks().get(0).code());
        assertEquals(RiskItem.CODE_AI_DEGRADED, content.risks().get(1).code());
    }

    @Test
    @DisplayName("六节任一为空都必须在构造期被拒绝, 不允许生成残缺报告")
    void everySectionIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(" ",
                List.of(citation()), List.of(risk()), rules(), new BigDecimal("0.5"), List.of("无")));
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(SupervisionResult.PASS_SUGGEST,
                List.of(), List.of(risk()), rules(), new BigDecimal("0.5"), List.of("无")));
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(SupervisionResult.PASS_SUGGEST,
                List.of(citation()), List.of(), rules(), new BigDecimal("0.5"), List.of("无")));
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(SupervisionResult.PASS_SUGGEST,
                List.of(citation()), List.of(risk()), null, new BigDecimal("0.5"), List.of("无")));
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(SupervisionResult.PASS_SUGGEST,
                List.of(citation()), List.of(risk()), rules(), new BigDecimal("1.5"), List.of("无")));
        assertThrows(IllegalArgumentException.class, () -> new ReportContent(SupervisionResult.PASS_SUGGEST,
                List.of(citation()), List.of(risk()), rules(), new BigDecimal("0.5"), List.of()));
    }

    @Test
    @DisplayName("引用 chunkId 非正数直接拒绝: 回链不了的引用等于没有引用")
    void citationRequiresPositiveChunkId() {
        assertThrows(IllegalArgumentException.class, () -> new CitationRef(0L, "a.pdf", 1, 1));
        assertEquals("[材料.pdf p.1]", new CitationRef(9L, "材料.pdf", 1, 1).text());
    }

    private static RuleEngineResult rules() {
        return new RuleEngineResult(new BigDecimal("240000.00"), new BigDecimal("300000.00"),
                "ADJUST_AMOUNT", new BigDecimal("60000.00"), new BigDecimal("240000.00"),
                new BigDecimal("1887.12"), new BigDecimal("1000.00"), List.of("超限建议降额"));
    }

    private static CitationRef citation() {
        return new CitationRef(1L, "材料.pdf", 1, 1);
    }

    private static RiskItem risk() {
        return new RiskItem(RiskItem.CODE_NONE, "未发现显著风险点", RiskItem.LEVEL_LOW);
    }
}
