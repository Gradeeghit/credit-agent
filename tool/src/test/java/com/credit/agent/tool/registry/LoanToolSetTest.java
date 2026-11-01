package com.credit.agent.tool.registry;

import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.rag.retrieval.VectorRetriever;
import com.credit.agent.tool.policy.PolicyTools;
import com.credit.agent.tool.product.ProductTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 工具子集单测: 按名取工具必须精确, 拼错名字必须立刻报错而不是静默少挂。 */
class LoanToolSetTest {

    private static LoanToolSet all() {
        return new LoanToolRegistry().loanToolSet(new ProductTools(mock(ProductService.class)),
                new PolicyTools(mock(VectorRetriever.class), 1000));
    }

    @Test
    @DisplayName("按名取子集: 制度域只拿 searchPolicy")
    void subsetPicksOnlyNamedTools() {
        LoanToolSet subset = all().subset("searchPolicy");

        assertEquals(1, subset.size());
        assertEquals("searchPolicy", subset.toolNames()[0]);
    }

    @Test
    @DisplayName("未登记的工具名直接抛错, 不返回空集合")
    void unknownNameFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> all().subset("queryBlacklist"));

        assertTrue(ex.getMessage().contains("queryBlacklist"));
        assertTrue(ex.getMessage().contains("searchPolicy"), "报错要列出已登记工具, 方便定位拼写");
    }
}
