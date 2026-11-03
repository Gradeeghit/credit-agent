package com.credit.agent.agent.llm;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.tool.registry.LoanToolSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 版模型网关: 唯一真正触达 ChatModel 的类。
 * 业务含义: 统一挂载 E 模块工具(查询类, 只查不算)、统一记录 token 与耗时(经 AiCallLogger)、
 * 统一把底层异常收敛成 {@link LlmCallException}, 让上游只关心"AI 可用/不可用"。
 * <p>
 * 为什么不用 {@code ChatClient}(重要): ChatClient 会在内部用 ToolCallingManager 自己跑完整个
 * 工具循环, 只把 <b>最后一次</b> ChatResponse 交出来。而供应商按 <b>每轮</b> 计费(每轮都重发完整历史),
 * 只记最后一轮会让 {@code ai_call_log} 系统性低报成本。这里改成自己驱动循环, 每轮各记一条,
 * 并把各轮 token 累加成 {@link LlmAnswer} 的总量(F2 预算终止依赖这个总量)。
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmGateway.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final LoanToolSet toolSet;
    private final AiCallLogger aiCallLogger;
    private final String model;
    private final boolean forceToolEnabled;
    private final int maxToolRounds;

    /**
     * @param model            当前 chat 模型, 强制工具时要显式带进请求(options 不一定与默认值合并)
     * @param forceToolEnabled 强制工具总开关; 端点不支持 tool_choice 时置 false 即可整体退回"模型自主决定"
     * @param maxToolRounds    一次 ask 内允许的最大模型调用轮数(含最终作答轮), 防止工具循环失控
     */
    public SpringAiLlmGateway(ChatModel chatModel, ToolCallingManager toolCallingManager,
                              LoanToolSet toolSet, AiCallLogger aiCallLogger,
                              @Value("${spring.ai.openai.chat.options.model:}") String model,
                              @Value("${credit-agent.agent.force-tool-enabled:true}") boolean forceToolEnabled,
                              @Value("${credit-agent.agent.max-tool-rounds:4}") int maxToolRounds) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolSet = toolSet;
        this.aiCallLogger = aiCallLogger;
        this.model = model;
        this.forceToolEnabled = forceToolEnabled;
        this.maxToolRounds = Math.max(1, maxToolRounds);
    }

    /**
     * 发起一次带工具能力的模型调用, 内部自行驱动工具循环。
     * 业务含义: 工具由网关统一挂载, 域 Agent 不各自拼装, 保证"能调哪些工具"只有一处可改;
     * 每一轮模型调用都单独记账, 让成本明细能还原到轮次。
     *
     * @param request 问题与 trace_id
     * @return 文本与 <b>各轮累计</b> token 用量
     * @throws LlmCallException 空问题、模型异常、响应为空、工具轮次超限
     */
    @Override
    public LlmAnswer ask(LlmRequest request) {
        if (request == null || request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new LlmCallException("模型请求为空");
        }
        ToolCallback[] callbacks = callbacksFor(request);
        List<Message> messages = initialMessages(request);
        long promptTokens = 0L;
        long completionTokens = 0L;

        for (int round = 1; round <= maxToolRounds; round++) {
            Prompt prompt = new Prompt(messages, optionsFor(request, callbacks, round));
            long startNanos = System.nanoTime();
            ChatResponse response = callModel(prompt);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;

            LlmAnswer roundAnswer = LlmResponses.toAnswer(response);
            promptTokens += roundAnswer.promptTokens();
            completionTokens += roundAnswer.completionTokens();

            // 每轮一条: 该轮的 prompt 已包含全部历史, 因此按轮求和才是真实消耗
            aiCallLogger.log(new AiCallRecord(request.traceId(), LlmResponses.modelOf(response),
                    roundAnswer.promptTokens(), roundAnswer.completionTokens(), latencyMs,
                    AiCallRecord.Kind.CHAT, round));

            if (!response.hasToolCalls()) {
                return new LlmAnswer(roundAnswer.text(), promptTokens, completionTokens);
            }
            if (round == maxToolRounds) {
                // 已花的钱都已记账, 这里中断只是不再往下烧; 上层按 LlmCallException 走降级
                throw new LlmCallException("工具调用轮次超限(" + maxToolRounds + " 轮), 已中断以防死循环");
            }
            messages = executeTools(prompt, response);
            log.debug("工具轮 round={} trace_id={} 已完成, 进入下一轮", round, request.traceId());
        }
        // maxToolRounds >= 1, 循环必在内部 return 或 throw; 此处仅为编译兜底
        throw new LlmCallException("工具调用轮次超限");
    }

    /**
     * 组装本轮请求参数。
     * 关键约束:
     * <ul>
     *   <li>{@code internalToolExecutionEnabled(false)} —— 不让 ChatModel 自己跑工具循环,
     *       否则一次 call 就吞掉所有轮次, 我们既看不到中间轮也记不了账;</li>
     *   <li>{@code toolChoice=required} 只在第 1 轮下发 —— 若每轮都强制,
     *       模型会被要求"每次都必须调工具", 形成必然超限的死循环。</li>
     * </ul>
     */
    private OpenAiChatOptions optionsFor(LlmRequest request, ToolCallback[] callbacks, int round) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(false);
        if (model != null && !model.isBlank()) {
            builder.model(model);
        }
        if (callbacks.length > 0) {
            builder.toolCallbacks(callbacks);
        }
        // 强制工具: 本域没工具时不下发(无工具的 tool_choice=required 会被端点判 400)
        if (round == 1 && request.forceTool() && forceToolEnabled && callbacks.length > 0) {
            builder.toolChoice("required");
        }
        return builder.build();
    }

    /**
     * 执行本轮工具调用, 拿到下一轮要发送的完整对话历史。
     * 本项目工具(queryProduct / searchPolicy)均未标记 returnDirect, 因此统一继续问模型;
     * 若将来加入 returnDirect 工具, 需在此判断 {@link ToolExecutionResult#returnDirect()} 并直接返回工具结果。
     */
    private List<Message> executeTools(Prompt prompt, ChatResponse response) {
        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
        List<Message> history = result == null ? null : result.conversationHistory();
        if (history == null || history.isEmpty()) {
            throw new LlmCallException("工具执行后对话历史为空");
        }
        return new ArrayList<>(history);
    }

    private ChatResponse callModel(Prompt prompt) {
        try {
            return chatModel.call(prompt);
        } catch (LlmCallException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmCallException("模型调用失败: " + e.getMessage(), e);
        }
    }

    /** 组装首轮消息: system 为空时不发空串消息(部分端点对空 system 会报错)。 */
    private static List<Message> initialMessages(LlmRequest request) {
        List<Message> messages = new ArrayList<>(2);
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.systemPrompt()));
        }
        messages.add(new UserMessage(request.userPrompt()));
        return messages;
    }

    /**
     * 取本次要挂的工具。
     * 业务含义: 请求没指定子集(null)时按全量挂载, 保持旧调用方行为;
     * 指定了就只挂这些 —— 制度域不会看到 queryProduct, 汇总域一个工具都看不到。
     */
    private ToolCallback[] callbacksFor(LlmRequest request) {
        if (request.toolNames() == null) {
            return toolSet.callbacks() == null ? new ToolCallback[0] : toolSet.callbacks();
        }
        return toolSet.subset(request.toolNames().toArray(new String[0])).callbacks();
    }
}
