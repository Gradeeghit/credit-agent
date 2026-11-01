package com.credit.agent.orchestration.application.observability;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.agent.infra.observability.ToolCallRecord;
import com.credit.agent.infra.observability.ToolCallRecorder;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具调用留痕落库(H1 的 Tool 段)。
 * 业务含义: 工具是模型在 ChatClient 内部循环里调的, 它天然属于"当前正在分析的这一步",
 * 因此不新开 step_index(会和编排层的步号撞唯一约束), 而是把 tool_name / 输出摘要 / 失败原因
 * 回填到该步那一条 approval_step 上 —— 给 trace_id 就能看到"这一步调了 searchPolicy、拿到了什么"。
 * 容错: 写失败只告警, 绝不影响工具返回值。
 */
@Component
public class DbToolCallRecorder implements ToolCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(DbToolCallRecorder.class);

    /** 摘要上限, 与 TraceRecorder 保持一致 */
    private static final int SUMMARY_LIMIT = 500;

    private final ApprovalStepService stepService;

    public DbToolCallRecorder(ApprovalStepService stepService) {
        this.stepService = stepService;
    }

    /**
     * 把一次工具调用回填到所属步骤。
     * 业务含义: 只写工具相关列, 不动 latency_ms —— 那是整步(含模型推理)的耗时, 覆盖会失真。
     *
     * @param record 追踪 ID、步序号、工具名与失败原因; 缺 traceId 或 stepIndex 时跳过
     */
    @Override
    public void record(ToolCallRecord record) {
        if (record == null || record.traceId() == null || record.stepIndex() == null) {
            return;
        }
        try {
            stepService.update(new UpdateWrapper<ApprovalStep>()
                    .eq("trace_id", record.traceId())
                    .eq("step_index", record.stepIndex())
                    .set("tool_name", record.toolName())
                    .set("output_summary", truncate(record.outputSummary()))
                    .set("failure_reason", record.failureReason()));
            log.debug("工具调用已回填: traceId={}, step={}, tool={}, {}ms",
                    record.traceId(), record.stepIndex(), record.toolName(), record.latencyMs());
        } catch (RuntimeException e) {
            log.warn("工具留痕写入失败, 已忽略: traceId={}, tool={}, err={}",
                    record.traceId(), record.toolName(), e.getMessage());
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "...";
    }
}
