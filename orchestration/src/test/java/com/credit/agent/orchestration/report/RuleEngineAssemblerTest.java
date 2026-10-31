package com.credit.agent.orchestration.report;

import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.orchestration.application.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 规则引擎装配单测: LPR=0、加点=0 时数字可手算核对。 */
class RuleEngineAssemblerTest {

    private final LoanApplicationService applicationService = mock(LoanApplicationService.class);
    private final ProductService productService = mock(ProductService.class);
    /** LPR 传 0 且产品加点 0, 让月供退化为 本金/期数, 便于手算 */
    private final RuleEngineAssembler assembler =
            new RuleEngineAssembler(applicationService, productService, BigDecimal.ZERO);

    @Test
    @DisplayName("未超限: 额度 240000, PASS, 月供 200000/12=16666.67, 手续费 200000×0.005=1000.00")
    void computesWithinLimit() {
        when(applicationService.getById(9L)).thenReturn(application("20000", "200000", 12, LoanApplication.INCOME_TYPE_MONTHLY));
        when(productService.getById(1L)).thenReturn(product());

        RuleEngineResult result = assembler.assemble(9L);

        assertEquals(new BigDecimal("240000.00"), result.theoreticalLimit());
        assertEquals("PASS", result.limitAction());
        assertEquals(new BigDecimal("200000.00"), result.suggestedAmount());
        assertEquals(new BigDecimal("16666.67"), result.monthlyPayment());
        assertEquals(new BigDecimal("1000.00"), result.fee());
    }

    @Test
    @DisplayName("超限: ADJUST_AMOUNT, 手续费基数为降额后的 240000 → 1200.00")
    void adjustsWhenOverLimit() {
        when(applicationService.getById(9L)).thenReturn(application("20000", "300000", 12, LoanApplication.INCOME_TYPE_MONTHLY));
        when(productService.getById(1L)).thenReturn(product());

        RuleEngineResult result = assembler.assemble(9L);

        assertEquals("ADJUST_AMOUNT", result.limitAction());
        assertEquals(new BigDecimal("60000.00"), result.exceedAmount());
        assertEquals(new BigDecimal("240000.00"), result.suggestedAmount());
        assertEquals(new BigDecimal("1200.00"), result.fee());
        assertEquals(new BigDecimal("20000.00"), result.monthlyPayment());
    }

    @Test
    @DisplayName("缺收入 → 400; 产品不存在 → 400; 申请单不存在 → 404")
    void rejectsIncompleteInput() {
        when(applicationService.getById(9L)).thenReturn(application(null, "200000", 12, LoanApplication.INCOME_TYPE_MONTHLY));
        when(productService.getById(1L)).thenReturn(product());
        assertEquals(400, assertThrows(BizException.class, () -> assembler.assemble(9L)).getStatus());

        when(applicationService.getById(8L)).thenReturn(application("20000", "200000", 12, LoanApplication.INCOME_TYPE_MONTHLY));
        when(productService.getById(1L)).thenReturn(null);
        assertEquals(400, assertThrows(BizException.class, () -> assembler.assemble(8L)).getStatus());

        when(applicationService.getById(7L)).thenReturn(null);
        assertEquals(404, assertThrows(BizException.class, () -> assembler.assemble(7L)).getStatus());
    }

    private static LoanApplication application(String income, String amount, int term, String incomeType) {
        LoanApplication application = new LoanApplication();
        application.setId(9L);
        application.setProductId(1L);
        application.setIncome(income == null ? null : new BigDecimal(income));
        application.setAmount(new BigDecimal(amount));
        application.setTermMonths(term);
        application.setIncomeType(incomeType);
        application.setStatus(LoanApplication.STATUS_SUBMITTED);
        return application;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(1L);
        product.setCode("P1");
        product.setName("薪金贷");
        product.setRateBp(0);
        product.setIncomeMultiple(new BigDecimal("12.00"));
        product.setMaxAmount(new BigDecimal("300000.00"));
        product.setFeeRate(new BigDecimal("0.0050"));
        product.setTermMinMonths(6);
        product.setTermMaxMonths(36);
        product.setStatus(Product.STATUS_ON);
        return product;
    }
}
