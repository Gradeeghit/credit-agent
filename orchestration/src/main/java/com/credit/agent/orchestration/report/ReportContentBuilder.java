package com.credit.agent.orchestration.report;

import com.credit.agent.orchestration.runtime.DomainConflict;
import com.credit.agent.orchestration.runtime.SupervisionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        if (supervision == null) {
            throw new IllegalArgumentException("汇聚结果不能为空");
        }
        return new ReportContent(supervision.conclusion(), supervision.citations(),
                risksOf(supervision), ruleEngine, confidenceOf(supervision.conclusion()),
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

    private static List<RiskItem> risksOf(SupervisionResult supervision) {
        if (!supervision.conflicts().isEmpty()) {
            return supervision.conflicts().stream()
                    .map(c -> new RiskItem(c.code(), c.summary(), RiskItem.LEVEL_HIGH))
                    .toList();
        }
        if (SupervisionResult.REJECT_SUGGEST.equals(supervision.conclusion())) {
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
