package com.credit.agent.orchestration.runtime;

import java.util.List;

/**
 * 一次 Agent 执行的结局。
 * 业务含义: 被终止条件打断时**必须返回已拿到的部分结果**(observations), 让上层能生成降级报告;
 * 状态取值与 approval_trace.status 一致(COMPLETED / FAILED), 失败必须带 failure_reason。
 *
 * @param status        COMPLETED(正常收敛) / FAILED(被终止条件打断)
 * @param failureReason FAILED 时的原因; COMPLETED 为 null
 * @param steps         实际执行步数(未发起的步骤不计)
 * @param tokensUsed    累计 token
 * @param observations  每步观察摘要, 即"部分结果"
 */
public record AgentRunResult(
        String status,
        FailureReason failureReason,
        int steps,
        long tokensUsed,
        List<String> observations) {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** 正常收敛。 */
    public static AgentRunResult completed(int steps, long tokensUsed, List<String> observations) {
        return new AgentRunResult(STATUS_COMPLETED, null, steps, tokensUsed, List.copyOf(observations));
    }

    /** 被终止条件打断, 保留部分结果。 */
    public static AgentRunResult failed(FailureReason reason, int steps, long tokensUsed,
                                        List<String> observations) {
        return new AgentRunResult(STATUS_FAILED, reason, steps, tokensUsed, List.copyOf(observations));
    }

    /** 是否正常收敛; 降级/终止一律为 false, 上层据此决定是否标 requires_human_review。 */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}
