package com.credit.agent.agent.llm;

import com.credit.agent.infra.observability.AiCallLogger;
import com.credit.agent.infra.observability.AiCallRecord;
import com.credit.agent.tool.registry.LoanToolSet;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring AI 版模型网关: 唯一真正触达 ChatModel 的类。
 * 业务含义: 统一挂载 E 模块工具(查询类, 只查不算)、统一记录 token 与耗时(经 AiCallLogger)、
 * 统一把底层异常收敛成 {@link LlmCallException}, 让上游只关心"AI 可用/不可用"。
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final LoanToolSet toolSet;
    private final AiCallLogger aiCallLogger;
    private final String model;
    private final boolean forceToolEnabled;

    /**
     * @param model            当前 chat 模型, 强制工具时要显式带进请求(options 不一定与默认值合并)
     * @param forceToolEnabled 强制工具总开关; 端点不支持 tool_choice 时置 false 即可整体退回"模型自主决定"
     */
    public SpringAiLlmGateway(ChatClient chatClient, LoanToolSet toolSet, AiCallLogger aiCallLogger,
                              @Value("${spring.ai.openai.chat.options.model:}") String model,
                              @Value("${credit-agent.agent.force-tool-enabled:true}") boolean forceToolEnabled) {
        this.chatClient = chatClient;
        this.toolSet = toolSet;
        this.aiCallLogger = aiCallLogger;
        this.model = model;
        this.forceToolEnabled = forceToolEnabled;
    }

    /**
     * 发起一次带工具能力的模型调用。
     * 业务含义: 工具由网关统一挂载, 域 Agent 不各自拼装, 保证"能调哪些工具"只有一处可改。
     *
     * @param request 问题与 trace_id
     * @return 文本与 token 用量
     * @throws LlmCallException 空问题、模型异常、响应为空
     */
    @Override
    public LlmAnswer ask(LlmRequest request) {
        if (request == null || request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new LlmCallException("模型请求为空");
        }
        long startNanos = System.nanoTime();
        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                    .system(request.systemPrompt() == null ? "" : request.systemPrompt())
                    .user(request.userPrompt());
            ToolCallback[] callbacks = callbacksFor(request);
            // 强制工具: 本域没工具时不下发(无工具的 tool_choice=required 会被端点判 400)
            if (request.forceTool() && forceToolEnabled && callbacks.length > 0) {
                spec = spec.options(forceToolOptions());
            }
            if (callbacks.length > 0) {
                spec = spec.toolCallbacks(callbacks);
            }
            ChatResponse response = spec.call().chatResponse();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            LlmAnswer answer = LlmResponses.toAnswer(response);
            aiCallLogger.log(new AiCallRecord(request.traceId(), LlmResponses.modelOf(response),
                    answer.promptTokens(), answer.completionTokens(), latencyMs));
            return answer;
        } catch (LlmCallException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmCallException("模型调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 强制工具时下发的 options: tool_choice=required。
     * 业务含义: 制度域只挂一个工具, required 等价于"必须先调 searchPolicy 取证再回答";
     * 用 required 而不是指定函数名, 是因为兼容端点对"指定函数"的支持参差不齐, 而 required 是 OpenAI 标准值。
     * 注意: 必须显式带上 model —— 请求级 options 不保证与 yml 默认值合并。
     */
    private OpenAiChatOptions forceToolOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().toolChoice("required");
        if (model != null && !model.isBlank()) {
            builder.model(model);
        }
        return builder.build();
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
