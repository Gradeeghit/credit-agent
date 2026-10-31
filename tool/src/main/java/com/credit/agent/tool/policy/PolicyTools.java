package com.credit.agent.tool.policy;

import com.credit.agent.rag.retrieval.Citation;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.VectorRetriever;
import com.credit.agent.tool.contract.ToolResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 制度检索 Tool(E3)。
 * 业务含义: 让 LLM 能引用真实制度条款而不是凭记忆作答, 每条结果都带 [文件名 p.N] 引用;
 * 检索超时不是致命错误 —— 返回 DEGRADED 空集合, 让 Agent 降级作答并标记 RETRIEVAL_DEGRADED, 流程不停。
 */
@Component
public class PolicyTools {

    private static final Logger log = LoggerFactory.getLogger(PolicyTools.class);

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final VectorRetriever vectorRetriever;
    private final long timeoutMs;
    private final ExecutorService executor;

    public PolicyTools(VectorRetriever vectorRetriever,
                       @Value("${credit-agent.tool.search-timeout-ms:3000}") long timeoutMs) {
        this.vectorRetriever = vectorRetriever;
        this.timeoutMs = timeoutMs;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "policy-tool");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 按关键词/问题检索制度条款。
     * 业务含义: 这是 Agent 引用的唯一来源; 超时返回 DEGRADED 而不是抛异常, 避免整条审批链路被检索拖死。
     *
     * @param keyword 检索关键词或自然语言问题, 不能为空
     * @param limit   召回条数, 1-10, 不传默认 5
     * @return 命中列表(带引用); 参数非法→PARAM_INVALID, 超时→DEGRADED/TOOL_TIMEOUT, 检索异常→RETRIEVAL_FAILED
     */
    @Tool(name = "searchPolicy",
            description = "检索信贷制度/年报条款, 返回带 [文件名 p.页码] 引用的片段。超时会降级返回空结果。")
    public ToolResponse<List<PolicyHit>> searchPolicy(
            @ToolParam(description = "检索关键词或问题, 例如: 逾期几次算不良") String keyword,
            @ToolParam(description = "召回条数, 1-10, 不传默认 5", required = false) Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return ToolResponse.error("PARAM_INVALID", "keyword 不能为空");
        }
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            return ToolResponse.error("PARAM_INVALID", "limit 必须在 1-" + MAX_LIMIT + " 之间");
        }
        try {
            List<RetrievedChunk> chunks = CompletableFuture
                    .supplyAsync(() -> vectorRetriever.search(keyword.strip(), size, null), executor)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            return ToolResponse.ok(chunks.stream().map(PolicyTools::toHit).toList());
        } catch (TimeoutException e) {
            log.warn("searchPolicy 超时: keyword={}, timeoutMs={}", keyword, timeoutMs);
            return ToolResponse.degraded("TOOL_TIMEOUT",
                    "制度检索超时(" + timeoutMs + "ms), 已降级返回空结果, 请在回答中说明未取到制度依据", List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResponse.degraded("TOOL_TIMEOUT", "制度检索被中断, 已降级返回空结果", List.of());
        } catch (ExecutionException e) {
            log.warn("searchPolicy 检索失败: keyword={}", keyword, e.getCause());
            return ToolResponse.error("RETRIEVAL_FAILED", "制度检索失败, 请改用材料内信息作答并标注缺失");
        }
    }

    /** 关闭内部线程池, 避免应用停止时线程泄漏。 */
    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static PolicyHit toHit(RetrievedChunk chunk) {
        return new PolicyHit(chunk.chunkId(), chunk.docId(), chunk.docName(), chunk.section(),
                chunk.content(), chunk.pageStart(), chunk.pageEnd(), chunk.score(),
                Citation.render(chunk.docName(), chunk.pageStart(), chunk.pageEnd()));
    }
}
