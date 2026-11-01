package com.credit.agent.agent.domain;

import com.credit.agent.agent.llm.LlmAnswer;
import com.credit.agent.agent.llm.LlmGateway;
import com.credit.agent.agent.llm.LlmRequest;
import com.credit.agent.domain.loan.StatementMonthlyAverage;
import com.credit.agent.rag.retrieval.Citation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个材料域 Agent(F3)。
 * 业务含义: 一次只回答本域问题, 输入被 {@link DomainBrief} 物理限定在本域证据内;
 * 输出只要"我这一域看到了什么 + 依据", 不下放贷结论, 也不做任何金额计算。
 */
@Component
public class DomainAgent {

    private final LlmGateway gateway;

    public DomainAgent(LlmGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 分析本域证据并给出域内结论。
     * 业务含义: 每个域独立跑一次, 互不可见; 结论冲突由 Supervisor 识别, 本类绝不"投票"。
     *
     * @param traceId 全链路追踪 ID
     * @param brief   本域上下文(其他域证据在构造时已固定为空)
     * @return 域内结论 + 引用 + token 消耗
     */
    public DomainAnswer analyze(String traceId, DomainBrief brief) {
        if (brief == null) {
            throw new IllegalArgumentException("DomainBrief 不能为空");
        }
        LlmAnswer answer = gateway.ask(new LlmRequest(traceId, systemPrompt(brief.role()), userPrompt(brief),
                toolNames(brief.role())));
        return new DomainAnswer(brief.role(), answer.text(), citations(brief), answer.totalTokens(),
                VerdictParser.parse(answer.text()), factsOf(brief, answer.text()));
    }

    /** 工具说明; 只有本域真正挂载的工具才会写进提示词, 避免模型去调一个根本没挂的工具。 */
    private static final Map<String, String> TOOL_DESCRIPTIONS = Map.of(
            "searchPolicy", "- searchPolicy(keyword, limit): 检索制度/年报条款, 返回正文与 [文件名 p.N] 引用。",
            "queryProduct", "- queryProduct(productId): 查询产品参数(利率加点/期限/倍数/上限/费率), 不含任何计算结果。");

    /** 取证规则: 不论本域有没有工具都成立, 所以与工具清单分开写。 */
    private static final String TOOL_RULES = """
            【工具使用规则】
            1. 【本域证据】不足以支持结论且本域有可用工具时, 必须先调用工具取证再作答; 不得凭常识或推测补全条款与参数。
            2. 工具返回的 citation 必须原样使用; 工具返回的正文同样要按 [文件名 p.N] 标注出处。
            3. 工具返回 status=DEGRADED(如 TOOL_TIMEOUT): 写明"依据缺失(工具降级: <code>)", 结论降级为"需人工补充依据"。
            4. 工具返回 status=ERROR: 说明缺哪项数据; 同一工具最多重试 1 次, 不得反复调用。
            5. 金额、利率、月供一律不自行计算, 需要数值时以规则引擎结果为准。
            """;

    /**
     * 本域允许调用的工具名(F3 分域降载)。
     * 业务含义: 制度域只给 searchPolicy, 其余取证域只给 queryProduct, 汇总域不给工具;
     * 这份清单同时决定提示词里出现哪些工具、以及 {@link LlmRequest} 让网关挂哪些回调, 两处不会再漂移。
     *
     * @param role 域角色
     * @return 工具名; 空列表表示该域不调用任何工具
     */
    public static List<String> toolNames(AgentRole role) {
        return switch (role) {
            case MATERIAL, CREDIT, STATEMENT -> List.of("queryProduct");
            case POLICY -> List.of("searchPolicy");
            case SUPERVISOR -> List.of();
        };
    }

    /** 按域生成"可用工具"段落; 无工具时明确写不可用, 防止模型臆造工具名。 */
    static String toolGuidance(AgentRole role) {
        List<String> names = toolNames(role);
        StringBuilder sb = new StringBuilder();
        if (names.isEmpty()) {
            sb.append("【可用工具】本域不提供任何工具, 只能依据【本域证据】与已有上下文作答。\n");
        } else {
            sb.append("【可用工具】(只查不算, 是取证手段, 不是结论来源)\n");
            for (String name : names) {
                sb.append(TOOL_DESCRIPTIONS.get(name)).append('\n');
            }
        }
        return sb.append(TOOL_RULES).toString();
    }

    /** 各域的角色约束: 只看本域、不许算钱、结论必须带引用。 */
    static String systemPrompt(AgentRole role) {
        return toolGuidance(role) + switch (role) {
            case MATERIAL -> "你是信贷初审的材料核验 Agent。只依据【本域证据】判断身份证、收入证明、用途说明是否齐全且自洽。"
                    + "禁止引用征信、银行流水、制度条款的内容; 禁止计算任何金额、利率、月供。";
            case CREDIT -> "你是信贷初审的征信分析 Agent。只依据【本域证据】识别逾期、频繁查询、负债偏高等风险信号。"
                    + "禁止引用收入证明、银行流水的内容; 禁止计算任何金额。";
            case STATEMENT -> "你是信贷初审的银行流水分析 Agent。只依据【本域证据】判断收支稳定性与异常大额往来。"
                    + "禁止引用征信、制度条款的内容; 禁止计算任何金额, 金额对比由规则引擎负责。"
                    + "回答末尾必须另起一行输出 JSON: {\"transactions\":[{\"date\":\"yyyy-MM-dd\",\"amount\":入账金额}]}, "
                    + "只列入账(正数), 不列支出, 金额用两位小数数字。";
            case POLICY -> "你是信贷初审的制度检索 Agent。只依据【本域证据】中的制度条款作答, 每条结论必须带 [文件名 p.N] 引用。"
                    + "禁止引用客户材料内容; 禁止计算任何金额。";
            case SUPERVISOR -> "你是汇总 Agent, 只做跨域冲突识别与结论汇聚, 不单独取证、不做金额计算, 也不调用任何工具。";
        };
    }

    /** 把本域证据按 [文件名 p.N] + 正文摊平, 证据为空时也要显式说明, 避免模型编造。 */
    static String userPrompt(DomainBrief brief) {
        StringBuilder sb = new StringBuilder("【本域证据】\n");
        if (brief.evidence().isEmpty()) {
            sb.append("(本域无可用证据: 请先按【可用工具】取证; 仍无结果时结论写\"需人工补充依据\")\n");
        }
        for (DomainEvidence evidence : brief.evidence()) {
            sb.append(Citation.render(evidence.docName(), evidence.pageStart(), evidence.pageEnd()))
                    .append('\n')
                    .append(evidence.content())
                    .append("\n---\n");
        }
        sb.append("请按以下格式回答:\n1) 结论: 正常 / 异常 / 需补材料\n2) 依据: 每条都要带引用\n3) 待补清单: 没有就写无");
        if (brief.role() == AgentRole.STATEMENT) {
            sb.append("\n4) 明细 JSON: {\"transactions\":[{\"date\":\"yyyy-MM-dd\",\"amount\":入账金额}]}"
                    + "(只列入账, 金额为两位小数数字, 不要写单位和千分位)");
        }
        return sb.toString();
    }

    /**
     * 汇总本域数值事实。
     * 业务含义: 库里带来的数值(如客户申报收入)原样保留; 流水月均由「模型抽取明细 + domain 算平均」得出,
     * 模型给不出合法明细就只是缺这条事实, 不会污染其他数值, 也不会让冲突检测误判。
     */
    private static Map<String, BigDecimal> factsOf(DomainBrief brief, String answerText) {
        Map<String, BigDecimal> facts = new LinkedHashMap<>(brief.facts());
        if (brief.role() == AgentRole.STATEMENT) {
            BigDecimal monthlyAvg = StatementMonthlyAverage.average(StatementExtractor.monthlyInflows(answerText));
            if (monthlyAvg != null) {
                facts.put(DomainAnswer.FACT_STATEMENT_MONTHLY_AVG, monthlyAvg);
            }
        }
        return facts;
    }

    private static List<CitationRef> citations(DomainBrief brief) {
        return brief.evidence().stream()
                .map(e -> new CitationRef(e.chunkId(), e.docName(), e.pageStart(), e.pageEnd()))
                .distinct()
                .toList();
    }
}
