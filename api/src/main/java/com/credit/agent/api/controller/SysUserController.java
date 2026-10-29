package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.SysUserAppService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.SysUserDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工账号接口。
 * 业务含义: 主管维护信审员/主管账号; 响应体不含密码摘要。
 */
@RestController
@RequestMapping("/api/sys-users")
public class SysUserController {

    private final SysUserAppService sysUserAppService;

    public SysUserController(SysUserAppService sysUserAppService) {
        this.sysUserAppService = sysUserAppService;
    }

    /** 分页查询账号。 */
    @GetMapping
    public ApiResponse<PageResult<SysUserDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(sysUserAppService.page(page, size, keyword));
    }

    /** 按主键查询账号。 */
    @GetMapping("/{id}")
    public ApiResponse<SysUserDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(sysUserAppService.get(id));
    }

    /** 新建账号。 */
    @PostMapping
    public ApiResponse<SysUserDto.View> create(@RequestBody SysUserDto.SaveRequest req) {
        return ApiResponse.ok(sysUserAppService.create(req));
    }

    /** 修改角色或启停状态。 */
    @PutMapping("/{id}")
    public ApiResponse<SysUserDto.View> update(@PathVariable Long id, @RequestBody SysUserDto.SaveRequest req) {
        return ApiResponse.ok(sysUserAppService.update(id, req));
    }

    /** 手工解锁账号。 */
    @PostMapping("/{id}/unlock")
    public ApiResponse<SysUserDto.View> unlock(@PathVariable Long id) {
        return ApiResponse.ok(sysUserAppService.unlock(id));
    }

    /** 删除账号; 已受理过申请单时返回 409。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysUserAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
