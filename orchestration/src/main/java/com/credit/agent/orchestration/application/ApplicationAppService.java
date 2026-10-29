package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.service.AppUserService;
import com.credit.agent.infra.persistence.service.DocumentService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.infra.persistence.service.ProductService;
import com.credit.agent.orchestration.application.dto.ApplicationDto;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 申请单应用服务(A1 提交 / A7 查状态)。
 * 业务含义: 受理客户申请并落库, 保证客户维度幂等;
 * 额度是否超限、月供多少由 domain 规则引擎计算, 本类不做任何金额运算。
 */
@Service
public class ApplicationAppService {

    private static final DateTimeFormatter APP_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LoanApplicationService applicationService;
    private final ProductService productService;
    private final AppUserService appUserService;
    private final DocumentService documentService;

    public ApplicationAppService(LoanApplicationService applicationService, ProductService productService,
                                 AppUserService appUserService, DocumentService documentService) {
        this.applicationService = applicationService;
        this.productService = productService;
        this.appUserService = appUserService;
        this.documentService = documentService;
    }

    /** 分页查询申请单, 可按状态与客户过滤; 按提交时间倒序。 */
    public PageResult<ApplicationDto.View> page(long page, long size, String status, Long customerId) {
        var wrapper = Wrappers.lambdaQuery(LoanApplication.class)
                .eq(status != null && !status.isBlank(), LoanApplication::getStatus, status)
                .eq(customerId != null, LoanApplication::getCustomerId, customerId)
                .orderByDesc(LoanApplication::getCreatedAt);
        Page<LoanApplication> result = applicationService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(ApplicationAppService::toView).toList());
    }

    /** 按主键查询申请单。 */
    public ApplicationDto.View get(Long id) {
        return toView(require(id));
    }

    /** 按对客申请编号查询, 供客户查进度(A7)。 */
    public ApplicationDto.View getByAppNo(String appNo) {
        LoanApplication entity = applicationService.getOne(
                Wrappers.lambdaQuery(LoanApplication.class).eq(LoanApplication::getAppNo, appNo), false);
        if (entity == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + appNo);
        }
        return toView(entity);
    }

    /** 提交申请: 幂等键命中时直接返回首次结果, 不再新建。 */
    public ApplicationDto.View submit(ApplicationDto.SubmitRequest req) {
        validate(req);
        if (req.clientRequestId() != null && !req.clientRequestId().isBlank()) {
            LoanApplication exists = applicationService.getOne(Wrappers.lambdaQuery(LoanApplication.class)
                    .eq(LoanApplication::getCustomerId, req.customerId())
                    .eq(LoanApplication::getClientRequestId, req.clientRequestId()), false);
            if (exists != null) {
                return toView(exists);
            }
        }
        LoanApplication entity = new LoanApplication();
        entity.setAppNo(nextAppNo());
        entity.setCustomerId(req.customerId());
        entity.setProductId(req.productId());
        entity.setClientRequestId(req.clientRequestId());
        entity.setAmount(req.amount());
        entity.setTermMonths(req.termMonths());
        entity.setPurpose(req.purpose());
        entity.setPurposeDesc(req.purposeDesc());
        entity.setIncome(req.income());
        entity.setIncomeType(req.incomeType() == null ? LoanApplication.INCOME_TYPE_MONTHLY : req.incomeType());
        entity.setStatus(LoanApplication.STATUS_SUBMITTED);
        applicationService.save(entity);
        return toView(require(entity.getId()));
    }

    /** 修改申请: 仅受理前(SUBMITTED)允许, 已受理后改动需走人工流程。 */
    public ApplicationDto.View update(Long id, ApplicationDto.UpdateRequest req) {
        LoanApplication entity = require(id);
        if (!LoanApplication.STATUS_SUBMITTED.equals(entity.getStatus())) {
            throw BizException.conflict("APPLICATION_LOCKED", "当前状态 " + entity.getStatus() + " 不允许修改");
        }
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("AMOUNT_INVALID", "申请金额必须大于 0");
        }
        if (req.termMonths() == null || req.termMonths() <= 0) {
            throw BizException.badRequest("TERM_INVALID", "申请期限必须大于 0");
        }
        entity.setAmount(req.amount());
        entity.setTermMonths(req.termMonths());
        entity.setPurpose(req.purpose());
        entity.setPurposeDesc(req.purposeDesc());
        entity.setIncome(req.income());
        if (req.incomeType() != null) {
            entity.setIncomeType(req.incomeType());
        }
        applicationService.updateById(entity);
        return toView(require(id));
    }

    /** 撤销申请: 仅 SUBMITTED 且未挂材料的申请单可删。 */
    public void delete(Long id) {
        LoanApplication entity = require(id);
        if (!LoanApplication.STATUS_SUBMITTED.equals(entity.getStatus())) {
            throw BizException.conflict("APPLICATION_LOCKED", "当前状态 " + entity.getStatus() + " 不允许撤销");
        }
        long docs = documentService.count(
                Wrappers.lambdaQuery(Document.class).eq(Document::getApplicationId, id));
        if (docs > 0) {
            throw BizException.conflict("APPLICATION_HAS_DOCUMENT", "申请单已上传 " + docs + " 份材料, 不允许撤销");
        }
        applicationService.removeById(id);
    }

    private void validate(ApplicationDto.SubmitRequest req) {
        if (req.customerId() == null || appUserService.getById(req.customerId()) == null) {
            throw BizException.badRequest("CUSTOMER_NOT_FOUND", "申请人不存在");
        }
        Product product = req.productId() == null ? null : productService.getById(req.productId());
        if (product == null) {
            throw BizException.badRequest("PRODUCT_NOT_FOUND", "产品不存在");
        }
        if (!Product.STATUS_ON.equals(product.getStatus())) {
            throw BizException.badRequest("PRODUCT_OFF_SHELF", "产品已下架, 不可申请");
        }
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("AMOUNT_INVALID", "申请金额必须大于 0");
        }
        if (req.amount().compareTo(product.getMaxAmount()) > 0) {
            throw BizException.badRequest("AMOUNT_EXCEED_LIMIT", "申请金额超过产品上限: " + product.getMaxAmount());
        }
        if (req.termMonths() == null
                || req.termMonths() < product.getTermMinMonths()
                || req.termMonths() > product.getTermMaxMonths()) {
            throw BizException.badRequest("TERM_OUT_OF_RANGE",
                    "期限需在 " + product.getTermMinMonths() + "-" + product.getTermMaxMonths() + " 个月之间");
        }
        if (req.purpose() == null || req.purpose().isBlank()) {
            throw BizException.badRequest("PURPOSE_REQUIRED", "贷款用途不能为空");
        }
        String incomeType = req.incomeType() == null ? LoanApplication.INCOME_TYPE_MONTHLY : req.incomeType();
        if (!LoanApplication.INCOME_TYPE_MONTHLY.equals(incomeType)
                && !LoanApplication.INCOME_TYPE_YEARLY.equals(incomeType)) {
            throw BizException.badRequest("INCOME_TYPE_INVALID", "收入口径只能是 MONTHLY 或 YEARLY");
        }
    }

    private LoanApplication require(Long id) {
        LoanApplication entity = applicationService.getById(id);
        if (entity == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + id);
        }
        return entity;
    }

    /** 生成对客申请编号: 日期 + 6 位随机数, 唯一性由库唯一约束兜底。 */
    private static String nextAppNo() {
        return "APP" + LocalDate.now().format(APP_NO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private static ApplicationDto.View toView(LoanApplication a) {
        return new ApplicationDto.View(a.getId(), a.getAppNo(), a.getCustomerId(), a.getProductId(),
                a.getClientRequestId(), a.getAmount(), a.getTermMonths(), a.getPurpose(), a.getPurposeDesc(),
                a.getIncome(), a.getIncomeType(), a.getStatus(), a.getCreatedBy(), a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
