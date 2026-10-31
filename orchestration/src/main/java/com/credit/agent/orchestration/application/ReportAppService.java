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
 * 初审报告查询与锁定守卫(G6/G3)。
 * 业务含义: 报告对外只读; 版本化保留历史, 签批按 (applicationId, version) 定位。
 * 唯一的写入口 {@link #updateContent} 存在的意义是"把锁定语义变成可测、可复用的一道闸":
 * 已锁定报告一律 409, 想改内容只能重新分析产出新版本。
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
        return toView(requireReport(id));
    }

    /**
     * 修改报告内容(G3: 已锁定必须 409)。
     * 业务含义: 报告一经生成即锁定, 内容不可变 —— 这是审计要求, 不是体验问题;
     * 内容有误只能重新分析, 生成 version+1 的新报告, 旧版本保留可查。
     *
     * @param id          报告 ID
     * @param contentJson 新的六节内容 JSON
     * @return 更新后的报告视图
     * @throws BizException 报告不存在(404) / 已锁定(409) / 内容为空(400)
     */
    public ReportDto.View updateContent(Long id, String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            throw BizException.badRequest("CONTENT_REQUIRED", "报告内容不能为空");
        }
        ApprovalReport report = requireReport(id);
        if (Boolean.TRUE.equals(report.getLocked())) {
            throw BizException.conflict("REPORT_LOCKED",
                    "报告已锁定(version=" + report.getVersion() + "), 内容不可修改, 请重新分析生成新版本");
        }
        ApprovalReport patch = new ApprovalReport();
        patch.setId(id);
        patch.setContentJson(contentJson);
        reportService.updateById(patch);
        return toView(requireReport(id));
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

    private ApprovalReport requireReport(Long id) {
        ApprovalReport entity = reportService.getById(id);
        if (entity == null) {
            throw BizException.notFound("REPORT_NOT_FOUND", "报告不存在: " + id);
        }
        return entity;
    }

    private static ReportDto.View toView(ApprovalReport r) {
        return new ReportDto.View(r.getId(), r.getApplicationId(), r.getTraceId(), r.getVersion(),
                r.getConclusion(), r.getContentJson(), r.getRequiresHumanReview(), r.getLocked(),
                r.getCreatedAt());
    }
}
