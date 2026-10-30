package com.credit.agent.api.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层装配。
 * 业务含义: 注册身份解析器, 让 Controller 以 {@code CurrentUser} 参数形式拿到操作人;
 * 是否强制要求身份由 credit-agent.security.identity-required 控制, 默认强制。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final boolean identityRequired;

    public WebMvcConfig(@Value("${credit-agent.security.identity-required:true}") boolean identityRequired) {
        this.identityRequired = identityRequired;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(identityRequired));
    }
}
