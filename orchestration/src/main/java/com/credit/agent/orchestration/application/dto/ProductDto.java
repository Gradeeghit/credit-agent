package com.credit.agent.orchestration.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品参数对外契约(J1)。
 * 业务含义: 产品参数是规则引擎算钱的唯一输入; 此处只搬运参数, 不做额度/利率/手续费计算。
 */
public final class ProductDto {

    private ProductDto() {
    }

    /** 新建/修改产品入参; 全部金额与费率使用 BigDecimal, 禁止 double。 */
    public record SaveRequest(
            String code,
            String name,
            String baseRateRef,
            Integer rateBp,
            Integer termMinMonths,
            Integer termMaxMonths,
            BigDecimal incomeMultiple,
            BigDecimal maxAmount,
            BigDecimal feeRate,
            String status) {
    }

    /** 产品视图, 只含对外可见字段。 */
    public record View(
            Long id,
            String code,
            String name,
            String baseRateRef,
            Integer rateBp,
            Integer termMinMonths,
            Integer termMaxMonths,
            BigDecimal incomeMultiple,
            BigDecimal maxAmount,
            BigDecimal feeRate,
            String status,
            LocalDateTime createdAt) {
    }
}
