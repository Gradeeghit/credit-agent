package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.TraceReplayService;
import com.credit.agent.orchestration.application.dto.TraceDto;
import com.credit.agent.orchestration.application.security.CurrentUser;
import com.credit.agent.orchestration.application.security.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trace 回放接口(H2)。
 * 业务含义: 审计与排障入口 —— 给定 trace_id 还原步骤、模型调用与成本; 只对内部角色开放。
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceReplayService traceReplayService;

    public TraceController(TraceReplayService traceReplayService) {
        this.traceReplayService = traceReplayService;
    }

    /** 按 trace_id 回放一次审批的完整过程。 */
    @GetMapping("/{traceId}")
    public ApiResponse<TraceDto.Replay> replay(CurrentUser user, @PathVariable String traceId) {
        user.requireAny(UserRole.CHECKER, UserRole.APPROVER);
        return ApiResponse.ok(traceReplayService.replay(traceId));
    }
}
