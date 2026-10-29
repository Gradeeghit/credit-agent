package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.ProductAppService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.ProductDto;
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
 * 产品参数接口(J1)。
 * 业务含义: 供主管维护算钱参数; Controller 只做协议适配, 业务规则全在 ProductAppService。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductAppService productAppService;

    public ProductController(ProductAppService productAppService) {
        this.productAppService = productAppService;
    }

    /** 分页查询产品列表。 */
    @GetMapping
    public ApiResponse<PageResult<ProductDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productAppService.page(page, size, keyword));
    }

    /** 查询单个产品。 */
    @GetMapping("/{id}")
    public ApiResponse<ProductDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(productAppService.get(id));
    }

    /** 新增产品。 */
    @PostMapping
    public ApiResponse<ProductDto.View> create(@RequestBody ProductDto.SaveRequest req) {
        return ApiResponse.ok(productAppService.create(req));
    }

    /** 修改产品参数。 */
    @PutMapping("/{id}")
    public ApiResponse<ProductDto.View> update(@PathVariable Long id, @RequestBody ProductDto.SaveRequest req) {
        return ApiResponse.ok(productAppService.update(id, req));
    }

    /** 删除产品; 已被申请单引用时返回 409。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
