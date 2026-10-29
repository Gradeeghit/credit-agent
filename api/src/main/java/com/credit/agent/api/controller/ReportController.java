package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.ReportAppService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.ReportDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 初审报告查询接口(G6)。
 * 业务含义: 报告只读且版本化, 签批动作走编排层的状态机, 不在此暴露写接口。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportAppService reportAppService;

    public ReportController(ReportAppService reportAppService) {
        this.reportAppService = reportAppService;
    }

    /** 分页查询报告, requiresHumanReview=true 即为待人工复核清单。 */
    @GetMapping
    public ApiResponse<PageResult<ReportDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) Boolean requiresHumanReview) {
        return ApiResponse.ok(reportAppService.page(page, size, applicationId, conclusion, requiresHumanReview));
    }

    /** 按主键查询报告。 */
    @GetMapping("/{id}")
    public ApiResponse<ReportDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(reportAppService.get(id));
    }

    /** 查询申请单的最新版本报告。 */
    @GetMapping("/latest/{applicationId}")
    public ApiResponse<ReportDto.View> latest(@PathVariable Long applicationId) {
        return ApiResponse.ok(reportAppService.latest(applicationId));
    }
}
