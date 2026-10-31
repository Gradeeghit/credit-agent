package com.credit.agent.tool.registry;

import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.rag.retrieval.VectorRetriever;
import com.credit.agent.tool.policy.PolicyTools;
import com.credit.agent.tool.product.ProductTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/** 工具注册单测: 暴露给 LLM 的工具清单必须与预期完全一致。 */
class LoanToolRegistryTest {

    @Test
    @DisplayName("只注册 E1 与 E3 两个工具, 名字固定, 防止误放行新工具")
    void exposesOnlyWhitelistedTools() {
        ProductTools productTools = new ProductTools(mock(ProductService.class));
        PolicyTools policyTools = new PolicyTools(mock(VectorRetriever.class), 1000);

        LoanToolSet toolSet = new LoanToolRegistry().loanToolSet(productTools, policyTools);

        assertEquals(2, toolSet.size());
        assertEquals(java.util.Set.of("queryProduct", "searchPolicy"),
                java.util.Set.copyOf(Arrays.asList(toolSet.toolNames())));
    }
}
