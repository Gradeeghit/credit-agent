package com.credit.agent.agent.domain;

import com.credit.agent.agent.llm.LlmAnswer;
import com.credit.agent.agent.llm.LlmGateway;
import com.credit.agent.agent.llm.LlmRequest;
import com.credit.agent.rag.retrieval.Citation;
import org.springframework.stereotype.Component;

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
        LlmAnswer answer = gateway.ask(new LlmRequest(traceId, systemPrompt(brief.role()), userPrompt(brief)));
        return new DomainAnswer(brief.role(), answer.text(), citations(brief), answer.totalTokens(),
                VerdictParser.parse(answer.text()), Map.of());
    }

    /** 各域的角色约束: 只看本域、不许算钱、结论必须带引用。 */
    static String systemPrompt(AgentRole role) {
        return switch (role) {
            case MATERIAL -> "你是信贷初审的材料核验 Agent。只依据【本域证据】判断身份证、收入证明、用途说明是否齐全且自洽。"
                    + "禁止引用征信、银行流水、制度条款的内容; 禁止计算任何金额、利率、月供。";
            case CREDIT -> "你是信贷初审的征信分析 Agent。只依据【本域证据】识别逾期、频繁查询、负债偏高等风险信号。"
                    + "禁止引用收入证明、银行流水的内容; 禁止计算任何金额。";
            case STATEMENT -> "你是信贷初审的银行流水分析 Agent。只依据【本域证据】判断收支稳定性与异常大额往来。"
                    + "禁止引用征信、制度条款的内容; 禁止计算任何金额, 金额对比由规则引擎负责。";
            case POLICY -> "你是信贷初审的制度检索 Agent。只依据【本域证据】中的制度条款作答, 每条结论必须带 [文件名 p.N] 引用。"
                    + "禁止引用客户材料内容; 禁止计算任何金额。";
            case SUPERVISOR -> "你是汇总 Agent, 只做跨域冲突识别与结论汇聚, 不单独取证、不做金额计算。";
        };
    }

    /** 把本域证据按 [文件名 p.N] + 正文摊平, 证据为空时也要显式说明, 避免模型编造。 */
    static String userPrompt(DomainBrief brief) {
        StringBuilder sb = new StringBuilder("【本域证据】\n");
        if (brief.evidence().isEmpty()) {
            sb.append("(本域无可用证据)\n");
        }
        for (DomainEvidence evidence : brief.evidence()) {
            sb.append(Citation.render(evidence.docName(), evidence.pageStart(), evidence.pageEnd()))
                    .append('\n')
                    .append(evidence.content())
                    .append("\n---\n");
        }
        sb.append("请按以下格式回答:\n1) 结论: 正常 / 异常 / 需补材料\n2) 依据: 每条都要带引用\n3) 待补清单: 没有就写无");
        return sb.toString();
    }

    private static List<String> citations(DomainBrief brief) {
        return brief.evidence().stream()
                .map(e -> Citation.render(e.docName(), e.pageStart(), e.pageEnd()))
                .distinct()
                .toList();
    }
}
