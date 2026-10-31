package com.credit.agent.tool.registry;

import com.credit.agent.tool.policy.PolicyTools;
import com.credit.agent.tool.product.ProductTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册装配(把 @Tool 方法暴露成 Function Calling 回调)。
 * 业务含义: LLM 能看到的工具清单由此处决定 —— 新增一个工具必须在 {@link #loanToolSet} 里显式登记,
 * 不靠包扫描自动放行, 避免"某个类随手加了 @Tool 就被模型调用"。
 */
@Configuration
public class LoanToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(LoanToolRegistry.class);

    /**
     * 注册当前对 LLM 开放的全部工具。
     * 业务含义: 现阶段只开放 E1 产品查询与 E3 制度检索(E2 黑名单待建表, E4 材料摘要属 P1)。
     */
    @Bean
    public LoanToolSet loanToolSet(ProductTools productTools, PolicyTools policyTools) {
        LoanToolSet toolSet = new LoanToolSet(ToolCallbacks.from(productTools, policyTools));
        log.info("已注册 Tool: {}", String.join(", ", toolSet.toolNames()));
        return toolSet;
    }
}
