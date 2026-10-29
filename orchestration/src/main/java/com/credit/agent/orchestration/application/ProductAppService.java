package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.ProductDto;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 产品参数应用服务(J1)。
 * 业务含义: 维护算钱所需的参数; 上下架状态与参数区间在此校验, 金额计算仍在 domain, 本类不做任何计息。
 */
@Service
public class ProductAppService {

    private final ProductService productService;
    private final LoanApplicationService loanApplicationService;

    public ProductAppService(ProductService productService, LoanApplicationService loanApplicationService) {
        this.productService = productService;
        this.loanApplicationService = loanApplicationService;
    }

    /** 分页查询产品, 支持按编码或名称模糊过滤。 */
    public PageResult<ProductDto.View> page(long page, long size, String keyword) {
        var wrapper = Wrappers.lambdaQuery(Product.class).orderByAsc(Product::getCode);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Product::getCode, keyword).or().like(Product::getName, keyword));
        }
        Page<Product> result = productService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(ProductAppService::toView).toList());
    }

    /** 按主键查询产品, 不存在返回 404。 */
    public ProductDto.View get(Long id) {
        return toView(require(id));
    }

    /** 新增产品: 编码唯一, 参数区间必须自洽。 */
    public ProductDto.View create(ProductDto.SaveRequest req) {
        validate(req);
        Long exists = productService.count(Wrappers.lambdaQuery(Product.class).eq(Product::getCode, req.code()));
        if (exists > 0) {
            throw BizException.conflict("PRODUCT_CODE_EXISTS", "产品编码已存在: " + req.code());
        }
        Product entity = new Product();
        apply(entity, req);
        productService.save(entity);
        return toView(entity);
    }

    /** 修改产品: 编码不可变更, 只允许调整参数与上下架状态。 */
    public ProductDto.View update(Long id, ProductDto.SaveRequest req) {
        Product entity = require(id);
        validate(req);
        apply(entity, req);
        productService.updateById(entity);
        return toView(require(id));
    }

    /** 删除产品: 已被申请单引用时禁止删除, 只能改为下架。 */
    public void delete(Long id) {
        require(id);
        long used = loanApplicationService.count(
                Wrappers.lambdaQuery(LoanApplication.class).eq(LoanApplication::getProductId, id));
        if (used > 0) {
            throw BizException.conflict("PRODUCT_IN_USE", "产品已被 " + used + " 笔申请单引用, 请改为下架");
        }
        productService.removeById(id);
    }

    private Product require(Long id) {
        Product entity = productService.getById(id);
        if (entity == null) {
            throw BizException.notFound("PRODUCT_NOT_FOUND", "产品不存在: " + id);
        }
        return entity;
    }

    private void validate(ProductDto.SaveRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw BizException.badRequest("PRODUCT_CODE_REQUIRED", "产品编码不能为空");
        }
        if (req.termMinMonths() == null || req.termMaxMonths() == null
                || req.termMinMonths() <= 0 || req.termMaxMonths() < req.termMinMonths()) {
            throw BizException.badRequest("PRODUCT_TERM_INVALID", "期限区间非法: 需满足 0 < 最短期限 <= 最长期限");
        }
        if (req.maxAmount() == null || req.maxAmount().compareTo(BigDecimal.ZERO) <= 0
                || req.incomeMultiple() == null || req.incomeMultiple().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("PRODUCT_AMOUNT_INVALID", "额度上限与收入倍数必须大于 0");
        }
        if (req.feeRate() == null || req.feeRate().compareTo(BigDecimal.ZERO) < 0
                || req.feeRate().compareTo(BigDecimal.ONE) >= 0) {
            throw BizException.badRequest("PRODUCT_FEE_INVALID", "手续费率必须落在 [0, 1) 区间");
        }
    }

    private void apply(Product entity, ProductDto.SaveRequest req) {
        entity.setCode(req.code());
        entity.setName(req.name());
        entity.setBaseRateRef(req.baseRateRef() == null || req.baseRateRef().isBlank() ? "LPR" : req.baseRateRef());
        entity.setRateBp(req.rateBp());
        entity.setTermMinMonths(req.termMinMonths());
        entity.setTermMaxMonths(req.termMaxMonths());
        entity.setIncomeMultiple(req.incomeMultiple());
        entity.setMaxAmount(req.maxAmount());
        entity.setFeeRate(req.feeRate());
        entity.setStatus(req.status() == null || req.status().isBlank() ? Product.STATUS_ON : req.status());
    }

    private static ProductDto.View toView(Product p) {
        return new ProductDto.View(p.getId(), p.getCode(), p.getName(), p.getBaseRateRef(), p.getRateBp(),
                p.getTermMinMonths(), p.getTermMaxMonths(), p.getIncomeMultiple(), p.getMaxAmount(),
                p.getFeeRate(), p.getStatus(), p.getCreatedAt());
    }
}
