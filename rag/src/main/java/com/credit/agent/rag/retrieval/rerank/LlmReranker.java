package com.credit.agent.rag.retrieval.rerank;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.infra.observability.TraceMdc;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LLM 的重排实现(C4)。
 * 业务含义: 用一次便宜的模型调用, 把粗排 top-N 里"真正回答问题的条款"顶到前面。
 * 为什么是"一次调用 + 输出编号排列"而不是"每条打分 N 次": 前者成本固定为 1 次调用,
 * 后者是 N 次; 且排序本来就是相对关系, 让模型直接给相对顺序比给绝对分数更稳。
 * <p>
 * 三条硬约束:
 * <ul>
 *   <li>**只重排不新增**: 返回的片段一定来自 candidates, 模型给出越界编号会被丢弃;</li>
 *   <li>**失败降级**: 模型异常/输出不可解析/编号全非法 → 返回原顺序, 不抛异常;</li>
 *   <li>**可开关**: {@code credit-agent.retrieval.rerank.enabled=false} 时整条路径不发起模型调用
 *       (PRD C4 要求"若 Recall@3 下降则默认关闭")。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "credit-agent.retrieval.rerank.provider", havingValue = "chat")
public class LlmReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

    private final ChatModel chatModel;
    private final AiCallLogger aiCallLogger;
    private final boolean enabled;
    private final String model;
    private final int maxCandidates;
    private final int contentLimit;

    /**
     * @param enabled       总开关, 取值以 application.yml 为准(当前默认为 true);
     *                      这里是缺省兜底值 false, 只有配置项整个缺失时才会生效
     * @param model         重排用的模型, 留空则复用 chat 默认模型
     * @param maxCandidates 一次最多给多少条打分(PRD C4: 仅对 top20 打分)
     * @param contentLimit  单条片段进 prompt 的截断长度, 防止一次重排吃掉几千 token
     */
    public LlmReranker(ChatModel chatModel, AiCallLogger aiCallLogger,
                       @Value("${credit-agent.retrieval.rerank.enabled:false}") boolean enabled,
                       @Value("${credit-agent.retrieval.rerank.model:}") String model,
                       @Value("${credit-agent.retrieval.rerank.max-candidates:20}") int maxCandidates,
                       @Value("${credit-agent.retrieval.rerank.content-limit:200}") int contentLimit) {
        this.chatModel = chatModel;
        this.aiCallLogger = aiCallLogger;
        this.enabled = enabled;
        this.model = model;
        this.maxCandidates = Math.max(1, maxCandidates);
        this.contentLimit = Math.max(20, contentLimit);
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        if (!enabled) {
            log.debug("rerank 已全局关闭, 按原顺序返回前 {} 条", topN);
            return head(candidates, topN);
        }
        if (candidates.size() <= 1) {
            return head(candidates, topN);
        }
        List<RetrievedChunk> pool = candidates.size() > maxCandidates
                ? List.copyOf(candidates.subList(0, maxCandidates))
                : List.copyOf(candidates);
        return head(doRerank(query, pool), topN);
    }

    /** 真正发起重排; 任何失败都退化为原顺序, 绝不把异常抛给检索链路。 */
    private List<RetrievedChunk> doRerank(String query, List<RetrievedChunk> pool) {
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(new Prompt(
                    List.of(new UserMessage(buildPrompt(query, pool))), options()));
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String text = textOf(response);
            recordUsage(response, latencyMs);

            List<Integer> order = RerankOrderParser.parse(text, pool.size());
            if (order == null) {
                log.warn("rerank 输出无法解析, 降级为原顺序: trace_id={}, head={}",
                        TraceMdc.traceId(), abbreviate(text, 120));
                return pool;
            }
            List<RetrievedChunk> reordered = new ArrayList<>(pool.size());
            for (int index : order) {
                reordered.add(pool.get(index));
            }
            return reordered;
        } catch (RuntimeException e) {
            log.warn("rerank 调用失败, 降级为原顺序: trace_id={}, err={}", TraceMdc.traceId(), e.getMessage());
            return pool;
        }
    }

    /**
     * 重排提示词。
     * 业务含义: 明确要求"编号从 1 开始、每个恰好一次", 减少模型漏项与越界;
     * 片段正文截断到 contentLimit —— 重排只需要判断"这段是不是讲这个问题", 不需要全文。
     */
    private String buildPrompt(String query, List<RetrievedChunk> pool) {
        StringBuilder sb = new StringBuilder(256 + pool.size() * contentLimit);
        sb.append("你是信贷制度检索的重排器, 判断每个候选片段与问题的相关性。\n")
                .append("只输出一个 JSON, 不要解释, 不要代码围栏。\n")
                .append("问题: ").append(query).append('\n')
                .append("候选片段:\n");
        for (int i = 0; i < pool.size(); i++) {
            sb.append('[').append(i + 1).append("] ")
                    .append(abbreviate(pool.get(i).content(), contentLimit)).append('\n');
        }
        sb.append("输出 {\"order\":[...]}: 数字为片段编号(从 1 开始), 最相关的在前, ")
                .append("必须包含 1 到 ").append(pool.size()).append(" 的每个编号且只出现一次。");
        return sb.toString();
    }

    /** temperature=0: 同一批候选每次重排出同一顺序, 否则评测不可复现。 */
    private OpenAiChatOptions options() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().temperature(0.0);
        if (model != null && !model.isBlank()) {
            builder.model(model.trim());
        }
        return builder.build();
    }

    /** 重排也是一次真实计费调用, 必须按 CLAUDE.md 走同一条记账口(走 ai_call_log)。 */
    private void recordUsage(ChatResponse response, long latencyMs) {
        try {
            long promptTokens = tokensOf(response, true);
            long completionTokens = tokensOf(response, false);
            String usedModel = modelOf(response);
            aiCallLogger.log(new AiCallRecord(TraceMdc.traceId(), usedModel,
                    promptTokens, completionTokens, latencyMs, AiCallRecord.Kind.CHAT, 1));
        } catch (RuntimeException e) {
            log.warn("rerank 用量记账失败, 已忽略: {}", e.getMessage());
        }
    }

    private static long tokensOf(ChatResponse response, boolean prompt) {
        Usage usage = usageOf(response);
        if (usage == null) {
            return 0L;
        }
        Number value = prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private static Usage usageOf(ChatResponse response) {
        try {
            ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
            return metadata == null ? null : metadata.getUsage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String modelOf(ChatResponse response) {
        try {
            String used = response.getMetadata().getModel();
            return used == null || used.isBlank() ? (model == null ? "unknown" : model) : used;
        } catch (RuntimeException e) {
            return model == null || model.isBlank() ? "unknown" : model;
        }
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static List<RetrievedChunk> head(List<RetrievedChunk> chunks, int topN) {
        return chunks.size() > topN ? List.copyOf(chunks.subList(0, topN)) : chunks;
    }

    private static String abbreviate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
