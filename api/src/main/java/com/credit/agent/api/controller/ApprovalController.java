package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.ApprovalAppService;
import com.credit.agent.orchestration.application.dto.ApprovalDecisionDto;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主管签批接口(G4)。
 * 业务含义: 只有 APPROVER 能签批; 签批必须带报告版本, 版本不匹配返回 409;
 * 重复提交同一动作返回首次结果(idempotent=true), 不会重复产生审批步骤。
 */
@RestController
@RequestMapping("/api/applications")
public class ApprovalController {

    private final ApprovalAppService approvalAppService;

    public ApprovalController(ApprovalAppService approvalAppService) {
        this.approvalAppService = approvalAppService;
    }

    /**
     * 对指定申请单执行签批: APPROVE(通过) / REJECT(驳回) / NEED_MORE_INFO(要求补材料)。
     * 业务含义: 这是人工决策的唯一入口, 也是 AI 结论转为终态的唯一路径。
     */
    @PostMapping("/{id}/decision")
    public ApiResponse<ApprovalDecisionDto.Result> decide(CurrentUser user, @PathVariable Long id,
                                                          @RequestBody ApprovalDecisionDto.Request req,
                                                          @RequestParam(required = false) String traceId) {
        user.requireAny(UserRole.APPROVER);
        return ApiResponse.ok(approvalAppService.decide(id, req, user, traceId));
    }
}
