package com.credit.agent.orchestration.application.observability;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.entity.ApprovalTrace;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import com.credit.agent.infra.persistence.service.ApprovalTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 审批链路落库(H1: Agent / Tool / DB 三段)。
 * 业务含义: 一次审批先在 approval_trace 落 RUNNING, 每执行一步落一条 approval_step,
 * 结束时把结论、耗时、token、成本汇总回填 —— 这样给定 trace_id 能还原整条过程。
 * 容错: 写失败只告警不阻断审批(可观测性缺失可以补, 审批中断不可接受)。
 */
@Service
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

    /** 摘要长度上限: 落库的是"可读摘要", 不是整段 prompt(避免把客户敏感信息全量入库) */
    private static final int SUMMARY_LIMIT = 500;

    private final ApprovalTraceService traceService;
    private final ApprovalStepService stepService;

    public TraceRecorder(ApprovalTraceService traceService, ApprovalStepService stepService) {
        this.traceService = traceService;
        this.stepService = stepService;
    }

    /** 开始一次审批追踪。 */
    public void start(String traceId, Long applicationId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        ApprovalTrace trace = new ApprovalTrace();
        trace.setTraceId(traceId);
        trace.setApplicationId(applicationId);
        trace.setStatus(ApprovalTrace.STATUS_RUNNING);
        trace.setRequiresHumanReview(true);
        try {
            traceService.save(trace);
        } catch (RuntimeException e) {
            log.warn("approval_trace 写入失败, 已忽略: traceId={}, err={}", traceId, e.getMessage());
        }
    }

    /**
     * 记录一步执行明细。
     *
     * @param stepIndex     步序号, 从 1 开始, 与 traceId 组成唯一约束
     * @param agentRole     执行该步的域角色
     * @param toolName      调用的工具名, 纯推理步为 null
     * @param inputSummary  输入摘要(截断)
     * @param outputSummary 输出摘要(截断)
     * @param latencyMs     本步耗时
     * @param failureReason 失败原因, 成功为 null
     */
    public void step(String traceId, int stepIndex, String agentRole, String toolName,
                     String inputSummary, String outputSummary, long latencyMs, String failureReason) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        ApprovalStep step = new ApprovalStep();
        step.setTraceId(traceId);
        step.setStepIndex(stepIndex);
        step.setAgentRole(agentRole);
        step.setToolName(toolName);
        step.setInputSummary(truncate(inputSummary));
        step.setOutputSummary(truncate(outputSummary));
        step.setLatencyMs(latencyMs);
        step.setFailureReason(failureReason);
        try {
            stepService.save(step);
        } catch (RuntimeException e) {
            log.warn("approval_step 写入失败, 已忽略: traceId={}, step={}, err={}",
                    traceId, stepIndex, e.getMessage());
        }
    }

    /**
     * 回填本次审批的终态与汇总数据。
     *
     * @param status             COMPLETED / FAILED
     * @param conclusion         结论(冗余汇总)
     * @param confidence         置信度
     * @param totalLatencyMs     全链路耗时
     * @param totalTokens        累计 token
     * @param totalCost          累计成本, 可为 null(单价未配置)
     * @param requiresHumanReview 是否需要人工复核
     * @param failureReason      失败原因, 成功为 null
     */
    public void finish(String traceId, String status, String conclusion, BigDecimal confidence,
                       long totalLatencyMs, long totalTokens, BigDecimal totalCost,
                       boolean requiresHumanReview, String failureReason) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            // 用显式列名的 UpdateWrapper 而非 lambda 版: 后者依赖 TableInfo 缓存,
            // 在无 Spring 容器的单测里会抛 can not find lambda cache(项目风险 9)
            traceService.update(new UpdateWrapper<ApprovalTrace>()
                    .eq("trace_id", traceId)
                    .set("status", status)
                    .set("conclusion", conclusion)
                    .set("confidence", confidence)
                    .set("total_latency_ms", totalLatencyMs)
                    .set("total_tokens", totalTokens)
                    .set("total_cost", totalCost)
                    .set("requires_human_review", requiresHumanReview)
                    .set("failure_reason", failureReason));
        } catch (RuntimeException e) {
            log.warn("approval_trace 回填失败, 已忽略: traceId={}, err={}", traceId, e.getMessage());
        }
    }

    /** 摘要截断: 只保留可读片段, 避免把整段 prompt 写进库。 */
    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "...";
    }
}
