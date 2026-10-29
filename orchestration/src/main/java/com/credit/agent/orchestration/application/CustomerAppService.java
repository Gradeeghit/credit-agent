package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.AppUser;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.AppUserService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.CustomerDto;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 客户档案应用服务。
 * 业务含义: 申请人建档与查询; 身份证号唯一性校验在此, 脱敏规则按 H5 在输出层处理。
 */
@Service
public class CustomerAppService {

    private final AppUserService appUserService;
    private final LoanApplicationService applicationService;

    public CustomerAppService(AppUserService appUserService, LoanApplicationService applicationService) {
        this.appUserService = appUserService;
        this.applicationService = applicationService;
    }

    /** 分页查询客户, 支持按姓名/手机号模糊过滤。 */
    public PageResult<CustomerDto.View> page(long page, long size, String keyword) {
        var wrapper = Wrappers.lambdaQuery(AppUser.class).orderByDesc(AppUser::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AppUser::getName, keyword).or().like(AppUser::getPhone, keyword));
        }
        Page<AppUser> result = appUserService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(CustomerAppService::toView).toList());
    }

    /** 按主键查询客户。 */
    public CustomerDto.View get(Long id) {
        return toView(require(id));
    }

    /** 建档: 身份证号必须唯一, 手机号做基本格式校验。 */
    public CustomerDto.View create(CustomerDto.SaveRequest req) {
        validate(req);
        long exists = appUserService.count(
                Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getIdCardNo, req.idCardNo()));
        if (exists > 0) {
            throw BizException.conflict("CUSTOMER_ID_CARD_EXISTS", "该身份证号已建档");
        }
        AppUser entity = new AppUser();
        entity.setName(req.name());
        entity.setIdCardNo(req.idCardNo());
        entity.setPhone(req.phone());
        appUserService.save(entity);
        return toView(require(entity.getId()));
    }

    /** 修改客户资料; 身份证号不可变更(唯一键), 只允许改姓名与手机号。 */
    public CustomerDto.View update(Long id, CustomerDto.SaveRequest req) {
        AppUser entity = require(id);
        if (req.phone() == null || !req.phone().matches("\\d{11}")) {
            throw BizException.badRequest("PHONE_INVALID", "手机号必须是 11 位数字");
        }
        entity.setName(req.name());
        entity.setPhone(req.phone());
        appUserService.updateById(entity);
        return toView(require(id));
    }

    /** 删除客户档案; 已有申请单时禁止删除。 */
    public void delete(Long id) {
        require(id);
        long used = applicationService.count(
                Wrappers.lambdaQuery(LoanApplication.class).eq(LoanApplication::getCustomerId, id));
        if (used > 0) {
            throw BizException.conflict("CUSTOMER_IN_USE", "该客户已有 " + used + " 笔申请单, 不允许删除");
        }
        appUserService.removeById(id);
    }

    private void validate(CustomerDto.SaveRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw BizException.badRequest("NAME_REQUIRED", "客户姓名不能为空");
        }
        if (req.idCardNo() == null || !req.idCardNo().matches("\\d{17}[\\dXx]")) {
            throw BizException.badRequest("ID_CARD_INVALID", "身份证号必须是 18 位, 末位可为 X");
        }
        if (req.phone() == null || !req.phone().matches("\\d{11}")) {
            throw BizException.badRequest("PHONE_INVALID", "手机号必须是 11 位数字");
        }
    }

    private AppUser require(Long id) {
        AppUser entity = appUserService.getById(id);
        if (entity == null) {
            throw BizException.notFound("CUSTOMER_NOT_FOUND", "客户不存在: " + id);
        }
        return entity;
    }

    private static CustomerDto.View toView(AppUser u) {
        return new CustomerDto.View(u.getId(), u.getName(), u.getIdCardNo(), u.getPhone(), u.getCreatedAt());
    }
}
