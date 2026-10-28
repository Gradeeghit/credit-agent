package com.credit.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 信贷申请智能审批助手启动入口。
 * 业务含义: 系统唯一入口, 只负责启动与组件扫描, 不放任何业务逻辑。
 */
@SpringBootApplication
public class CreditAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditAgentApplication.class, args);
    }
}