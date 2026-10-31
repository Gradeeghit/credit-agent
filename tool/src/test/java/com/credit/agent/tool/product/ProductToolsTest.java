package com.credit.agent.tool.product;

import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.tool.contract.ToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E1 queryProduct 单测: 命中、不存在、参数非法、查询异常四类分支。 */
class ProductToolsTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductTools tools = new ProductTools(productService);

    @Test
    @DisplayName("命中产品: 返回参数原值, 金额仍是 BigDecimal")
    void foundProduct() {
        when(productService.getById(1L)).thenReturn(product());

        ToolResponse<ProductInfo> response = tools.queryProduct(1L);

        assertTrue(response.isOk());
        assertEquals("P1", response.data().code());
        assertEquals(new BigDecimal("300000.00"), response.data().maxAmount());
        assertEquals(new BigDecimal("0.0050"), response.data().feeRate());
    }

    @Test
    @DisplayName("产品不存在: NOT_FOUND, 不抛异常")
    void notFoundReturnsError() {
        when(productService.getById(99L)).thenReturn(null);

        ToolResponse<ProductInfo> response = tools.queryProduct(99L);

        assertEquals("ERROR", response.status());
        assertEquals("NOT_FOUND", response.code());
    }

    @Test
    @DisplayName("productId 为 null 或非正数: PARAM_INVALID, 且不查库")
    void invalidParamRejectedBeforeQuery() {
        assertEquals("PARAM_INVALID", tools.queryProduct(null).code());
        assertEquals("PARAM_INVALID", tools.queryProduct(0L).code());
        verify(productService, never()).getById(0L);
    }

    @Test
    @DisplayName("底层查询抛异常: 转成 QUERY_FAILED 错误对象, 不让堆栈进 LLM")
    void queryFailureIsWrapped() {
        when(productService.getById(2L)).thenThrow(new IllegalStateException("db down"));

        ToolResponse<ProductInfo> response = tools.queryProduct(2L);

        assertEquals("QUERY_FAILED", response.code());
        assertEquals("ERROR", response.status());
    }

    private static Product product() {
        Product p = new Product();
        p.setId(1L);
        p.setCode("P1");
        p.setName("薪金贷");
        p.setBaseRateRef("LPR");
        p.setRateBp(100);
        p.setTermMinMonths(6);
        p.setTermMaxMonths(36);
        p.setIncomeMultiple(new BigDecimal("12.00"));
        p.setMaxAmount(new BigDecimal("300000.00"));
        p.setFeeRate(new BigDecimal("0.0050"));
        p.setStatus("ON");
        return p;
    }
}
