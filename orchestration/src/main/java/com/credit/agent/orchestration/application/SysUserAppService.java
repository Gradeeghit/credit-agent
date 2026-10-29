package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.entity.SysUser;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.infra.persistence.service.SysUserService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.SysUserDto;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 员工账号应用服务。
 * 业务含义: 维护 CHECKER / APPROVER 账号与锁定状态;
 * 密码摘要的生成依赖 BCrypt(属待确认依赖), 本类不落密码, 因此新建账号后不可登录。
 */
@Service
public class SysUserAppService {

    private final SysUserService sysUserService;
    private final LoanApplicationService applicationService;

    public SysUserAppService(SysUserService sysUserService, LoanApplicationService applicationService) {
        this.sysUserService = sysUserService;
        this.applicationService = applicationService;
    }

    /** 分页查询账号, 支持按用户名模糊过滤。 */
    public PageResult<SysUserDto.View> page(long page, long size, String keyword) {
        var wrapper = Wrappers.lambdaQuery(SysUser.class).orderByDesc(SysUser::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysUser::getUsername, keyword);
        }
        Page<SysUser> result = sysUserService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(SysUserAppService::toView).toList());
    }

    /** 按主键查询账号。 */
    public SysUserDto.View get(Long id) {
        return toView(require(id));
    }

    /** 新建账号: 用户名唯一, 角色与状态必须是枚举值, 初始密码摘要为空。 */
    public SysUserDto.View create(SysUserDto.SaveRequest req) {
        validate(req);
        long exists = sysUserService.count(
                Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getUsername, req.username()));
        if (exists > 0) {
            throw BizException.conflict("USERNAME_EXISTS", "用户名已存在: " + req.username());
        }
        SysUser entity = new SysUser();
        entity.setUsername(req.username());
        entity.setRole(req.role());
        entity.setStatus(req.status() == null || req.status().isBlank() ? SysUser.STATUS_ACTIVE : req.status());
        entity.setFailCount(0);
        sysUserService.save(entity);
        return toView(require(entity.getId()));
    }

    /** 修改角色或启停状态; 用户名不可变更。 */
    public SysUserDto.View update(Long id, SysUserDto.SaveRequest req) {
        SysUser entity = require(id);
        validate(req);
        entity.setRole(req.role());
        if (req.status() != null && !req.status().isBlank()) {
            entity.setStatus(req.status());
        }
        sysUserService.updateById(entity);
        return toView(require(id));
    }

    /** 解锁账号: 清空锁定时间与失败次数, 供主管手工解封。 */
    public SysUserDto.View unlock(Long id) {
        require(id);
        sysUserService.update(Wrappers.lambdaUpdate(SysUser.class)
                .eq(SysUser::getId, id)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getFailCount, 0));
        return toView(require(id));
    }

    /** 删除账号; 曾受理过申请单的账号不允许删除, 只能停用。 */
    public void delete(Long id) {
        require(id);
        long used = applicationService.count(
                Wrappers.lambdaQuery(LoanApplication.class).eq(LoanApplication::getCreatedBy, id));
        if (used > 0) {
            throw BizException.conflict("USER_IN_USE", "该账号已受理 " + used + " 笔申请单, 请改为停用");
        }
        sysUserService.removeById(id);
    }

    private void validate(SysUserDto.SaveRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            throw BizException.badRequest("USERNAME_REQUIRED", "用户名不能为空");
        }
        if (!SysUser.ROLE_CHECKER.equals(req.role()) && !SysUser.ROLE_APPROVER.equals(req.role())) {
            throw BizException.badRequest("ROLE_INVALID", "角色只能是 CHECKER 或 APPROVER");
        }
        if (req.status() != null && !req.status().isBlank()
                && !SysUser.STATUS_ACTIVE.equals(req.status()) && !SysUser.STATUS_DISABLED.equals(req.status())) {
            throw BizException.badRequest("STATUS_INVALID", "状态只能是 ACTIVE 或 DISABLED");
        }
    }

    private SysUser require(Long id) {
        SysUser entity = sysUserService.getById(id);
        if (entity == null) {
            throw BizException.notFound("USER_NOT_FOUND", "账号不存在: " + id);
        }
        return entity;
    }

    private static SysUserDto.View toView(SysUser u) {
        return new SysUserDto.View(u.getId(), u.getUsername(), u.getRole(), u.getStatus(),
                u.getFailCount(), u.getLockedUntil(), u.getCreatedAt());
    }
}
