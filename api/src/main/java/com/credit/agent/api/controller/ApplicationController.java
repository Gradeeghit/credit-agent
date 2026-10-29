package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.ApplicationAppService;
import com.credit.agent.orchestration.application.dto.ApplicationDto;
import com.credit.agent.orchestration.application.dto.PageResult;
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
 * 申请单接口(A1 提交 / A7 查状态)。
 * 业务含义: 客户与信审员共用的申请单入口; 状态迁移由编排层状态机负责, 此处不提供改状态接口。
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationAppService applicationAppService;

    public ApplicationController(ApplicationAppService applicationAppService) {
        this.applicationAppService = applicationAppService;
    }

    /** 分页查询申请单, 可按状态/客户过滤。 */
    @GetMapping
    public ApiResponse<PageResult<ApplicationDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(applicationAppService.page(page, size, status, customerId));
    }

    /** 按主键查询申请单。 */
    @GetMapping("/{id}")
    public ApiResponse<ApplicationDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(applicationAppService.get(id));
    }

    /** 按对客申请编号查询进度。 */
    @GetMapping("/by-no/{appNo}")
    public ApiResponse<ApplicationDto.View> getByAppNo(@PathVariable String appNo) {
        return ApiResponse.ok(applicationAppService.getByAppNo(appNo));
    }

    /** 提交申请; 同一客户重复提交同一 clientRequestId 返回首次结果。 */
    @PostMapping
    public ApiResponse<ApplicationDto.View> submit(@RequestBody ApplicationDto.SubmitRequest req) {
        return ApiResponse.ok(applicationAppService.submit(req));
    }

    /** 受理前修改申请信息。 */
    @PutMapping("/{id}")
    public ApiResponse<ApplicationDto.View> update(@PathVariable Long id,
                                                   @RequestBody ApplicationDto.UpdateRequest req) {
        return ApiResponse.ok(applicationAppService.update(id, req));
    }

    /** 撤销申请; 已受理或已挂材料时返回 409。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        applicationAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
