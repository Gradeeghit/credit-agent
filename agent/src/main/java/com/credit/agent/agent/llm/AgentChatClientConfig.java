package com.credit.agent.agent.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配。
 * 业务含义: 模型连接参数走 application.yml 的 `spring.ai.openai.*`(DeepSeek), 这里只负责把 Builder 定型成
 * 唯一一个可注入的 ChatClient, 避免各模块各建一个连接池/各带一套默认参数。
 */
@Configuration
public class AgentChatClientConfig {

    /** 信贷审批用的 ChatClient; 后续多模型对比时在此扩展。 */
    @Bean
    public ChatClient creditChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
