package com.credit.agent.orchestration.runtime;

import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.DomainAnswer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 收入一致性与冲突检测(F4)。
 * 业务含义: 「收入证明说 3 万、流水月均 2 万」这类矛盾是初审的核心价值点, 必须由确定性代码发现 ——
 * 不能让模型自己判断, 更不能取平均或投票; 偏差超阈值就标 CONFLICT 转人工。
 * 数值一律 BigDecimal, 只做比例比较, 不做任何放贷金额计算。
 */
@Component
public class IncomeConsistencyDetector {

    /** PRD §12-2 的占位阈值 30%, 口径待本人拍板后改配置即可 */
    private final BigDecimal tolerance;

    public IncomeConsistencyDetector(
            @Value("${credit-agent.agent.conflict.income-tolerance:0.30}") BigDecimal tolerance) {
        this.tolerance = tolerance == null ? new BigDecimal("0.30") : tolerance;
    }

    /**
     * 检测收入证明与流水月均的偏差。
     * 业务含义: 任一域缺失该数值就不判冲突(缺证据不是矛盾, 走"需补材料"路径)。
     *
     * @param answers 各域结论
     * @return 冲突列表; 无冲突返回空列表(空列表代表"一致", 不代表"未检测")
     */
    public List<DomainConflict> detect(List<DomainAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        DomainAnswer material = answerOf(answers, AgentRole.MATERIAL);
        DomainAnswer statement = answerOf(answers, AgentRole.STATEMENT);
        if (material == null || statement == null) {
            return List.of();
        }
        BigDecimal income = material.fact(DomainAnswer.FACT_INCOME_MONTHLY);
        BigDecimal monthlyAvg = statement.fact(DomainAnswer.FACT_STATEMENT_MONTHLY_AVG);
        if (income == null || monthlyAvg == null || income.signum() <= 0) {
            return List.of();
        }
        BigDecimal deviation = income.subtract(monthlyAvg).abs()
                .divide(income, 4, RoundingMode.HALF_UP);
        if (deviation.compareTo(tolerance) <= 0) {
            return List.of();
        }
        return List.of(new DomainConflict(DomainConflict.CODE_INCOME_INCONSISTENT,
                summary(income, monthlyAvg, deviation),
                mergeCitations(material, statement)));
    }

    /** 阈值口径, 供自检与报告展示。 */
    public BigDecimal tolerance() {
        return tolerance;
    }

    private String summary(BigDecimal income, BigDecimal monthlyAvg, BigDecimal deviation) {
        return "收入证明月收入 " + income.toPlainString()
                + " 元, 与流水月均 " + monthlyAvg.toPlainString()
                + " 元偏差 " + percent(deviation) + "(相对收入证明)"
                + ", 超过阈值 " + percent(tolerance) + ", 转人工核对";
    }

    private static String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static List<String> mergeCitations(DomainAnswer left, DomainAnswer right) {
        return Stream.concat(left.citations().stream(), right.citations().stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static DomainAnswer answerOf(List<DomainAnswer> answers, AgentRole role) {
        return answers.stream().filter(a -> a != null && a.role() == role).findFirst().orElse(null);
    }
}
