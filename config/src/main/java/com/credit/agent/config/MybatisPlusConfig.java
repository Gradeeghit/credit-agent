package com.credit.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 装配。
 * 业务含义: 必须显式配置 PostgreSQL 分页插件, 否则 selectPage 会忽略分页条件全量返回,
 * 在报告列表、待复核清单等查询上会直接把库拖垮。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册分页拦截器, 方言固定为 PostgreSQL。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
