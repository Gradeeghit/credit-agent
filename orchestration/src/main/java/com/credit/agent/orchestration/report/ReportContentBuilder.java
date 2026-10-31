package com.credit.agent.orchestration.report;

import com.credit.agent.orchestration.runtime.DomainConflict;
import com.credit.agent.orchestration.runtime.FailureReason;
import com.credit.agent.orchestration.runtime.SupervisionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 由汇聚结果组装报告六节内容(G1)。
 * 业务含义: 报告是给人签批的, 所以"风险点/待补清单"即使为空也必须给出明确占位,
 * 不能留空白让审批人猜; 置信度按结论固定取值(确定性查表, 不让模型自评)。
 */
@Service
public class ReportContentBuilder {

    /** 无风险占位描述 */
    static final String NO_RISK = "未发现显著风险点";

    /** 无需补充材料的占位描述 */
    static final String NO_PENDING = "无";

    /**
     * 组装报告内容。
     * 业务含义: 冲突一律进风险点(HIGH), 结论为建议拒绝但无冲突时补一条域异常风险, 二者都没有才用 NONE 占位。
     *
     * @param supervision  汇聚结果(结论、冲突、引用、复核标记)
     * @param ruleEngine   规则引擎结果, 数字必须来自 domain
     * @param pendingItems 各域给出的待补清单, 为空时按结论给占位
     * @return 六节完整的报告内容
     */
    public ReportContent build(SupervisionResult supervision, RuleEngineResult ruleEngine,
                               List<String> pendingItems) {
        return build(supervision, ruleEngine, pendingItems, null);
    }

    /**
     * 组装报告内容(支持降级标记)。
     * 业务含义: 编排被预算/步数掐断时仍要出报告, 但必须在风险点里显式写「AI 分析不可用」,
     * 否则审批人会以为这是一份完整分析。
     *
     * @param failureReason 终止原因; 为 null 表示分析完整
     */
    public ReportContent build(SupervisionResult supervision, RuleEngineResult ruleEngine,
                               List<String> pendingItems, FailureReason failureReason) {
        return build(supervision, ruleEngine, pendingItems, failureReason,
                supervision == null ? null : supervision.conclusion());
    }

    /**
     * 组装报告内容(支持降级标记 + 降级前结论)。
     * 业务含义: 降级会把结论覆盖成 NEED_MORE_INFO, 但**降级前若已发现域异常, 那条异常不能从风险点里消失**,
     * 否则审批人只看到"AI 分析不可用", 不知道已经有域报过警。
     *
     * @param failureReason          终止原因; 为 null 表示分析完整
     * @param conclusionBeforeDegrade 降级前的汇聚结论, 用于决定是否补 DOMAIN_ABNORMAL 风险
     */
    public ReportContent build(SupervisionResult supervision, RuleEngineResult ruleEngine,
                               List<String> pendingItems, FailureReason failureReason,
                               String conclusionBeforeDegrade) {
        if (supervision == null) {
            throw new IllegalArgumentException("汇聚结果不能为空");
        }
        List<RiskItem> risks = new ArrayList<>(risksOf(supervision, conclusionBeforeDegrade));
        if (failureReason != null) {
            risks.add(new RiskItem(RiskItem.CODE_AI_DEGRADED,
                    "AI 分析不可用(" + failureReason + "), 报告仅含已完成部分, 请人工补核",
                    RiskItem.LEVEL_HIGH));
        }
        return new ReportContent(supervision.conclusion(), supervision.citations(),
                risks, ruleEngine, confidenceOf(supervision.conclusion()),
                pendingOf(supervision, pendingItems));
    }

    /** 置信度查表: 结论越需要人判断, 机器自评越低。口径待业务确认。 */
    static BigDecimal confidenceOf(String conclusion) {
        return switch (conclusion == null ? "" : conclusion) {
            case SupervisionResult.PASS_SUGGEST -> new BigDecimal("0.90");
            case SupervisionResult.REJECT_SUGGEST -> new BigDecimal("0.60");
            case SupervisionResult.NEED_MORE_INFO -> new BigDecimal("0.50");
            case SupervisionResult.CONFLICT -> new BigDecimal("0.30");
            default -> new BigDecimal("0.50");
        };
    }

    /** 风险点来源: 冲突优先; 其次看"当前结论或降级前结论"是否为建议拒绝, 两者任一命中都要保留域异常风险。 */
    private static List<RiskItem> risksOf(SupervisionResult supervision, String conclusionBeforeDegrade) {
        if (!supervision.conflicts().isEmpty()) {
            return supervision.conflicts().stream()
                    .map(c -> new RiskItem(c.code(), c.summary(), RiskItem.LEVEL_HIGH))
                    .toList();
        }
        boolean domainAbnormal = SupervisionResult.REJECT_SUGGEST.equals(supervision.conclusion())
                || SupervisionResult.REJECT_SUGGEST.equals(conclusionBeforeDegrade);
        if (domainAbnormal) {
            return List.of(new RiskItem(RiskItem.CODE_DOMAIN_ABNORMAL,
                    "子域发现异常信号, 详见各域结论", RiskItem.LEVEL_HIGH));
        }
        return List.of(new RiskItem(RiskItem.CODE_NONE, NO_RISK, RiskItem.LEVEL_LOW));
    }

    private static List<String> pendingOf(SupervisionResult supervision, List<String> pendingItems) {
        if (pendingItems != null && !pendingItems.isEmpty()) {
            return pendingItems.stream().filter(s -> s != null && !s.isBlank()).toList();
        }
        if (SupervisionResult.NEED_MORE_INFO.equals(supervision.conclusion())) {
            return List.of("待补材料明细缺失, 请按各域结论核对");
        }
        return List.of(NO_PENDING);
    }
}
