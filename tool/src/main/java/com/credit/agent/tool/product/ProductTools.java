package com.credit.agent.tool.product;

import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.tool.contract.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 产品查询 Tool(E1)。
 * 业务含义: 让 LLM 能回答"这个产品利率多少、能贷多久、额度倍数是多少";
 * 只做查询, 绝不做额度/月供/手续费计算 —— 那是 domain 规则引擎的职责。
 */
@Component
public class ProductTools {

    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    private final ProductService productService;

    public ProductTools(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 按产品 ID 查询产品参数。
     * 业务含义: 供 Agent 在解释产品口径时取证; 查不到或参数非法都返回结构化错误, 不抛异常给 LLM。
     *
     * @param productId 产品 ID, 必须是正整数
     * @return 产品参数; 参数非法→PARAM_INVALID, 不存在→NOT_FOUND, 查询异常→QUERY_FAILED
     */
    @Tool(name = "queryProduct",
            description = "查询信贷产品参数(利率加点/期限区间/收入倍数/额度上限/手续费率)。只查参数不做金额计算。")
    public ToolResponse<ProductInfo> queryProduct(
            @ToolParam(description = "产品ID, 正整数, 例如 1") Long productId) {
        if (productId == null || productId <= 0) {
            return ToolResponse.error("PARAM_INVALID", "productId 必须是正整数");
        }
        try {
            Product product = productService.getById(productId);
            if (product == null) {
                return ToolResponse.error("NOT_FOUND", "产品不存在: " + productId);
            }
            return ToolResponse.ok(toInfo(product));
        } catch (RuntimeException e) {
            log.warn("queryProduct 查询失败: productId={}", productId, e);
            return ToolResponse.error("QUERY_FAILED", "产品查询失败, 请稍后重试");
        }
    }

    /** 实体转对外快照, 只保留算钱需要的参数, 不外泄库内字段。 */
    private static ProductInfo toInfo(Product p) {
        return new ProductInfo(p.getId(), p.getCode(), p.getName(), p.getBaseRateRef(), p.getRateBp(),
                p.getTermMinMonths(), p.getTermMaxMonths(), p.getIncomeMultiple(), p.getMaxAmount(),
                p.getFeeRate(), p.getStatus());
    }
}
