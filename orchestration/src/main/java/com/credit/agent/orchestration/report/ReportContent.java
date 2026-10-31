package com.credit.agent.orchestration.report;

import com.credit.agent.agent.domain.CitationRef;

import java.math.BigDecimal;
import java.util.List;

/**
 * 初审报告的六节结构(G1), 对应 approval_report.content_json。
 * 业务含义: 六节缺一不可 —— 没有结论的报告没人敢签, 没有引用的结论无法核对,
 * 没有规则引擎结果就没有算钱的依据, 没有待补清单就无法要求客户补充。
 * 因此本类在**构造时**就拒绝任何空节, 而不是靠下游判断。
 *
 * @param conclusion  第 1 节 结论: PASS_SUGGEST / REJECT_SUGGEST / NEED_MORE_INFO / CONFLICT
 * @param citations   第 2 节 依据引用: 每条都带 chunkId, 可回链 document_chunk
 * @param risks       第 3 节 风险点: 无风险时保留 NONE 占位
 * @param ruleEngine  第 4 节 规则引擎结果: 额度/月供/手续费等确定性计算结果
 * @param confidence  第 5 节 置信度: 0-1 之间的小数
 * @param pendingItems 第 6 节 待补清单: 无需补充时填"无"
 */
public record ReportContent(
        String conclusion,
        List<CitationRef> citations,
        List<RiskItem> risks,
        RuleEngineResult ruleEngine,
        BigDecimal confidence,
        List<String> pendingItems) {

    public ReportContent {
        requireText(conclusion, 1, "结论");
        if (citations == null || citations.isEmpty()) {
            throw new IllegalArgumentException("报告第 2 节(依据引用)不能为空: 无引用视为不合格");
        }
        citations = List.copyOf(citations);
        if (risks == null || risks.isEmpty()) {
            throw new IllegalArgumentException("报告第 3 节(风险点)不能为空: 无风险时请放 NONE 占位");
        }
        risks = List.copyOf(risks);
        if (ruleEngine == null) {
            throw new IllegalArgumentException("报告第 4 节(规则引擎结果)不能为空");
        }
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("报告第 5 节(置信度)必须是 0-1 之间的小数");
        }
        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new IllegalArgumentException("报告第 6 节(待补清单)不能为空: 无需补充时请填「无」");
        }
        pendingItems = List.copyOf(pendingItems);
    }

    /** 是否存在跨域冲突结论。 */
    public boolean isConflict() {
        return "CONFLICT".equals(conclusion);
    }

    private static void requireText(String value, int section, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("报告第 " + section + " 节(" + name + ")不能为空");
        }
    }
}
