package com.credit.agent.agent.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 子 Agent 的域内结论。
 * 业务含义: 每个域只回答"我这一域看到了什么、依据是什么", 不下"放不放贷"的结论;
 * verdict 是给 Supervisor 的结构化判据, facts 是本域陈述的**原始数值**(如收入证明月收入),
 * citations 是带 chunkId 的结构化引用, 保证报告里的每条引用都能回链原文。
 *
 * @param role       产出该结论的域
 * @param conclusion 域内结论文本(异常/正常/需补材料)
 * @param citations  结构化引用, 每条都能回链到 document_chunk
 * @param tokensUsed 本次调用消耗的 token
 * @param verdict    结论等级, 由文本折叠而来(F4 汇聚判据)
 * @param facts      本域陈述的数值事实, key 见 {@link #FACT_INCOME_MONTHLY} 等常量
 */
public record DomainAnswer(
        AgentRole role,
        String conclusion,
        List<CitationRef> citations,
        long tokensUsed,
        DomainVerdict verdict,
        Map<String, BigDecimal> facts) {

    /** 收入证明申报的月收入(元), 由材料域给出 */
    public static final String FACT_INCOME_MONTHLY = "income.monthly";

    /** 流水推算的月均入账(元), 由流水域给出 */
    public static final String FACT_STATEMENT_MONTHLY_AVG = "statement.monthlyAvg";

    public DomainAnswer {
        verdict = verdict == null ? DomainVerdict.NEED_MORE_INFO : verdict;
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /** 只有文本、没有数值事实时的便捷构造(结论等级从文本折叠)。 */
    public DomainAnswer(AgentRole role, String conclusion, List<CitationRef> citations, long tokensUsed) {
        this(role, conclusion, citations, tokensUsed, VerdictParser.parse(conclusion), Map.of());
    }

    /** 取本域陈述的某项数值, 没有则返回 null(不补 0, 避免伪造事实)。 */
    public BigDecimal fact(String key) {
        return facts.get(key);
    }

    /** 本域是否给出异常结论。 */
    public boolean isAbnormal() {
        return verdict == DomainVerdict.ABNORMAL;
    }

    /** 本域是否要求补材料。 */
    public boolean needsMoreInfo() {
        return verdict == DomainVerdict.NEED_MORE_INFO;
    }

    /** 渲染后的引用文本, 供报告与日志展示; 落库请存 {@link #citations()}。 */
    public List<String> citationTexts() {
        return citations.stream().map(CitationRef::text).toList();
    }
}
