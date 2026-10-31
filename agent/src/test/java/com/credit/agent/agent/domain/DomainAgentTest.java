package com.credit.agent.agent.domain;

import com.credit.agent.agent.llm.LlmAnswer;
import com.credit.agent.agent.llm.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F3 域 Agent 单测: 提示词只含本域证据、引用渲染、token 回传。 */
class DomainAgentTest {

    private final AtomicReference<LlmRequest> captured = new AtomicReference<>();
    private final DomainAgent agent = new DomainAgent(request -> {
        captured.set(request);
        return new LlmAnswer("1) 正常 2) 依据见引用 3) 无", 120, 30);
    });

    @Test
    @DisplayName("材料域: prompt 只含材料证据, 不含征信/流水内容, 并写死禁止算钱")
    void materialPromptIsIsolated() {
        DomainBrief brief = DomainBrief.forMaterial(9L, List.of(
                new DomainEvidence(1L, 11L, "身份证.pdf", 1, 1, "姓名张三"),
                new DomainEvidence(5L, 11L, "收入证明.pdf", 2, 2, "月收入 3 万元")));

        DomainAnswer answer = agent.analyze("trace-1", brief);

        LlmRequest request = captured.get();
        assertTrue(request.systemPrompt().contains("材料核验"));
        assertTrue(request.systemPrompt().contains("禁止计算任何金额"));
        assertTrue(request.userPrompt().contains("姓名张三"));
        assertTrue(request.userPrompt().contains("[收入证明.pdf p.2]"));
        assertFalse(request.userPrompt().contains("逾期"));
        assertFalse(request.userPrompt().contains("月均入账"));
        assertEquals(AgentRole.MATERIAL, answer.role());
        assertEquals(150, answer.tokensUsed());
    }

    @Test
    @DisplayName("征信域与流水域各自只拿到自己的证据: 两个 prompt 互不串味")
    void creditAndStatementDoNotLeak() {
        DomainBrief credit = DomainBrief.forCredit(9L,
                List.of(new DomainEvidence(2L, 12L, "征信报告.pdf", 2, 3, "近半年逾期 2 次")));
        DomainBrief statement = DomainBrief.forStatement(9L,
                List.of(new DomainEvidence(3L, 13L, "流水.pdf", 4, 5, "月均入账 2 万元")));

        agent.analyze("trace-1", credit);
        String creditPrompt = captured.get().userPrompt();
        agent.analyze("trace-1", statement);
        String statementPrompt = captured.get().userPrompt();

        assertTrue(creditPrompt.contains("逾期 2 次"));
        assertFalse(creditPrompt.contains("月均入账"));
        assertTrue(statementPrompt.contains("月均入账"));
        assertFalse(statementPrompt.contains("逾期 2 次"));
    }

    @Test
    @DisplayName("制度域: 结论引用按 [文件名 p.起-止] 渲染并去重")
    void policyCitationsRenderedAndDistinct() {
        DomainBrief brief = DomainBrief.forPolicy(List.of(
                new DomainEvidence(4L, 14L, "2024年报.pdf", 12, 13, "逾期 3 次以上不予准入"),
                new DomainEvidence(6L, 14L, "2024年报.pdf", 12, 13, "同一页的第二段")));

        DomainAnswer answer = agent.analyze("trace-1", brief);

        assertEquals(List.of("[2024年报.pdf p.12-13]"), answer.citations());
        assertTrue(captured.get().systemPrompt().contains("制度检索"));
    }

    @Test
    @DisplayName("空证据: prompt 明确写(本域无可用证据), 不让模型自行发挥")
    void emptyEvidenceIsExplicit() {
        agent.analyze("trace-1", DomainBrief.forPolicy(List.of()));

        assertTrue(captured.get().userPrompt().contains("本域无可用证据"));
    }

    @Test
    @DisplayName("brief 为空直接拒绝, 不发起模型调用")
    void nullBriefRejected() {
        assertThrows(IllegalArgumentException.class, () -> agent.analyze("trace-1", null));
    }
}
