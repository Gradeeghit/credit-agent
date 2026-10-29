package com.credit.agent.orchestration.application.dto;

import java.time.LocalDateTime;

/**
 * 客户档案对外契约。
 * 业务含义: 申请人建档与查询; 身份证号、手机号属敏感字段,
 * 接入身份模块(I)后必须按角色脱敏, 当前仅返回明文供联调。
 */
public final class CustomerDto {

    private CustomerDto() {
    }

    /** 建档/修改入参。 */
    public record SaveRequest(String name, String idCardNo, String phone) {
    }

    /** 客户视图。 */
    public record View(Long id, String name, String idCardNo, String phone, LocalDateTime createdAt) {
    }
}
