package com.credit.agent.agent.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配。
 * 业务含义: 模型连接参数走 application.yml 的 `spring.ai.openai.*`(DeepSeek), 这里只负责把 Builder 定型成
 * 唯一一个可注入的 ChatClient, 避免各模块各建一个连接池/各带一套默认参数。
 */
@Configuration
public class AgentChatClientConfig {

    /**
     * 信贷审批用的 ChatClient; 后续多模型对比时在此扩展。
     * 业务含义: 模型请求/响应日志(SimpleLoggerAdvisor)属横切关注点, 在这里统一装配一次,
     * 而不是每次调用都 new 一个 —— 后者会让每个调用点都要记得加, 也污染 prompt 组装代码。
     * 注意: 该 advisor 内部只用 DEBUG 级别, 需 logging.level....SimpleLoggerAdvisor=debug 才输出。
     */
    @Bean
    public ChatClient creditChatClient(ChatClient.Builder builder) {
        return builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }
}
