package com.credit.agent.orchestration.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报告"规则引擎结果"节。
 * 业务含义: 这一节的所有数字都来自 `domain` 的确定性计算(D2 额度 / D3 超限 / D4 月供 / D5 手续费),
 * 本类只做**搬运与展示**, 绝不在这里做任何金额运算; 金额一律 BigDecimal, 与 NUMERIC(18,2) 对齐。
 *
 * @param theoreticalLimit 可批额度(D2, 已含产品上限封顶)
 * @param requestedAmount  客户申请金额
 * @param limitAction      PASS / ADJUST_AMOUNT(D3 刻意无 REJECT)
 * @param exceedAmount     超出金额, PASS 时为 0
 * @param suggestedAmount  D3 建议金额(降额到万)
 * @param monthlyPayment   月供(D4, 等额本息)
 * @param fee              手续费(D5, 基数为降额后放款额)
 * @param notes            规则引擎的说明文字
 */
public record RuleEngineResult(
        BigDecimal theoreticalLimit,
        BigDecimal requestedAmount,
        String limitAction,
        BigDecimal exceedAmount,
        BigDecimal suggestedAmount,
        BigDecimal monthlyPayment,
        BigDecimal fee,
        List<String> notes) {

    public RuleEngineResult {
        requireAmount(theoreticalLimit, "可批额度");
        requireAmount(requestedAmount, "申请金额");
        requireAmount(exceedAmount, "超出金额");
        requireAmount(suggestedAmount, "建议金额");
        requireAmount(monthlyPayment, "月供");
        requireAmount(fee, "手续费");
        if (limitAction == null || limitAction.isBlank()) {
            throw new IllegalArgumentException("规则引擎结果缺少 limitAction");
        }
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** 是否被建议降额。 */
    public boolean adjusted() {
        return "ADJUST_AMOUNT".equals(limitAction);
    }

    private static void requireAmount(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("规则引擎结果的" + name + "不能为空");
        }
    }
}
