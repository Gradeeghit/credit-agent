package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.ApprovalReport;
import com.credit.agent.infra.persistence.service.ApprovalReportService;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.ReportDto;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 初审报告查询服务(G6)。
 * 业务含义: 报告只读; 版本化保留历史, 签批按 (applicationId, version) 定位;
 * 报告生成与锁定由编排层负责, 本类不提供写入。
 */
@Service
public class ReportAppService {

    private final ApprovalReportService reportService;

    public ReportAppService(ApprovalReportService reportService) {
        this.reportService = reportService;
    }

    /** 分页查询报告, 可按申请单/结论/是否待复核过滤; 版本倒序。 */
    public PageResult<ReportDto.View> page(long page, long size, Long applicationId,
                                           String conclusion, Boolean requiresHumanReview) {
        var wrapper = Wrappers.lambdaQuery(ApprovalReport.class)
                .eq(applicationId != null, ApprovalReport::getApplicationId, applicationId)
                .eq(conclusion != null && !conclusion.isBlank(), ApprovalReport::getConclusion, conclusion)
                .eq(requiresHumanReview != null, ApprovalReport::getRequiresHumanReview, requiresHumanReview)
                .orderByDesc(ApprovalReport::getCreatedAt)
                .orderByDesc(ApprovalReport::getVersion);
        Page<ApprovalReport> result = reportService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(ReportAppService::toView).toList());
    }

    /** 按主键查询报告。 */
    public ReportDto.View get(Long id) {
        ApprovalReport entity = reportService.getById(id);
        if (entity == null) {
            throw BizException.notFound("REPORT_NOT_FOUND", "报告不存在: " + id);
        }
        return toView(entity);
    }

    /** 查询申请单的最新版本报告; 无报告时返回 404, 表示尚未生成初审结论。 */
    public ReportDto.View latest(Long applicationId) {
        ApprovalReport entity = reportService.getOne(Wrappers.lambdaQuery(ApprovalReport.class)
                .eq(ApprovalReport::getApplicationId, applicationId)
                .orderByDesc(ApprovalReport::getVersion)
                .last("LIMIT 1"), false);
        if (entity == null) {
            throw BizException.notFound("REPORT_NOT_FOUND", "申请单暂无初审报告: " + applicationId);
        }
        return toView(entity);
    }

    private static ReportDto.View toView(ApprovalReport r) {
        return new ReportDto.View(r.getId(), r.getApplicationId(), r.getTraceId(), r.getVersion(),
                r.getConclusion(), r.getContentJson(), r.getRequiresHumanReview(), r.getLocked(),
                r.getCreatedAt());
    }
}
