package com.credit.agent.orchestration.report;

import com.credit.agent.domain.loan.AmountLimitCheck;
import com.credit.agent.domain.loan.CreditLimitCalculator;
import com.credit.agent.domain.loan.FeeCalculator;
import com.credit.agent.domain.loan.IncomeBasis;
import com.credit.agent.domain.loan.InstalmentCalculator;
import com.credit.agent.domain.loan.Pricing;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则引擎结果装配(关闭"报告生成没有调用方"的缺口)。
 * 业务含义: 把申请单 + 产品参数喂给 `domain` 的确定性计算器, 组装出报告第 4 节所需的全部数字;
 * 本类**只做输入装配与结果搬运**, 一切乘法/除法/取整都在 domain 里, 绝不在这里算钱。
 * LPR 取值属 PRD §12-5 待确认项, 由配置注入(默认 3.45%), 不写死在代码里。
 */
@Service
public class RuleEngineAssembler {

    private final LoanApplicationService applicationService;
    private final ProductService productService;
    private final BigDecimal lpr;

    public RuleEngineAssembler(LoanApplicationService applicationService, ProductService productService,
                               @Value("${credit-agent.rule.lpr:0.0345}") BigDecimal lpr) {
        this.applicationService = applicationService;
        this.productService = productService;
        this.lpr = lpr == null ? new BigDecimal("0.0345") : lpr;
    }

    /**
     * 按申请单装配规则引擎结果。
     * 业务含义: 报告里的额度/月供/手续费必须有可手算核对的口径, 因此缺收入就直接拒绝而不是"按 0 算"。
     *
     * @param applicationId 申请单 ID
     * @return 报告第 4 节所需的确定性结果
     * @throws BizException 申请单不存在(404)、产品不存在(400)、收入缺失(400)
     */
    public RuleEngineResult assemble(Long applicationId) {
        LoanApplication application = applicationService.getById(applicationId);
        if (application == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + applicationId);
        }
        Product product = productService.getById(application.getProductId());
        if (product == null) {
            throw BizException.badRequest("PRODUCT_NOT_FOUND", "申请单关联的产品不存在: " + application.getProductId());
        }
        if (application.getIncome() == null || application.getIncome().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("INCOME_REQUIRED", "缺少收入/流水, 无法计算可批额度");
        }
        if (application.getTermMonths() == null || application.getTermMonths() <= 0) {
            throw BizException.badRequest("TERM_INVALID", "申请期限非法, 无法计算月供");
        }

        IncomeBasis basis = LoanApplication.INCOME_TYPE_YEARLY.equals(application.getIncomeType())
                ? IncomeBasis.YEARLY : IncomeBasis.MONTHLY;
        BigDecimal limit = CreditLimitCalculator.theoreticalLimit(application.getIncome(), basis,
                product.getIncomeMultiple(), product.getMaxAmount());
        AmountLimitCheck check = AmountLimitCheck.check(application.getAmount(), limit);
        BigDecimal actualAmount = check.action() == AmountLimitCheck.LimitAction.PASS
                ? application.getAmount() : check.suggestedAmount();
        BigDecimal annualRate = Pricing.annualRate(lpr, product.getRateBp());
        BigDecimal monthlyPayment = InstalmentCalculator.monthlyPayment(
                actualAmount, annualRate, application.getTermMonths());
        BigDecimal fee = FeeCalculator.fee(actualAmount, product.getFeeRate());

        return new RuleEngineResult(limit, application.getAmount(), check.action().name(),
                check.exceedAmount(), check.suggestedAmount(), monthlyPayment, fee,
                List.of(check.reason(),
                        "年化利率 " + annualRate.toPlainString() + "(LPR " + lpr.toPlainString()
                                + " + " + product.getRateBp() + "bp)"));
    }
}
