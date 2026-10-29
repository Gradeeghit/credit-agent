package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.CustomerAppService;
import com.credit.agent.orchestration.application.dto.CustomerDto;
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
 * 客户档案接口。
 * 业务含义: 申请人建档、查询与维护; 敏感字段脱敏待身份模块(I)落地后在输出层统一处理。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerAppService customerAppService;

    public CustomerController(CustomerAppService customerAppService) {
        this.customerAppService = customerAppService;
    }

    /** 分页查询客户。 */
    @GetMapping
    public ApiResponse<PageResult<CustomerDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(customerAppService.page(page, size, keyword));
    }

    /** 按主键查询客户。 */
    @GetMapping("/{id}")
    public ApiResponse<CustomerDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(customerAppService.get(id));
    }

    /** 客户建档。 */
    @PostMapping
    public ApiResponse<CustomerDto.View> create(@RequestBody CustomerDto.SaveRequest req) {
        return ApiResponse.ok(customerAppService.create(req));
    }

    /** 修改客户姓名与手机号。 */
    @PutMapping("/{id}")
    public ApiResponse<CustomerDto.View> update(@PathVariable Long id, @RequestBody CustomerDto.SaveRequest req) {
        return ApiResponse.ok(customerAppService.update(id, req));
    }

    /** 删除客户档案; 已有申请单时返回 409。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
