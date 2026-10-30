package com.credit.agent.domain.validation;

import java.math.BigDecimal;

/**
 * 申请提交的领域入参(D6)。
 * 业务含义: 校验规则属于业务, 必须能脱离 Web 层与数据库单测,
 * 所以这里不引用任何 DTO 或实体, 只保留原始字段。
 *
 * @param customerId      申请人 ID
 * @param productId       产品 ID
 * @param clientRequestId 客户端幂等键, 可为空
 * @param amount          申请金额(元)
 * @param termMonths      申请期限(月)
 * @param purpose         贷款用途类型
 * @param purposeDesc     用途说明, 敏感词闸门的输入
 * @param income          月收入或年流水, 可为空
 * @param incomeType      收入口径: MONTHLY / YEARLY
 */
public record ApplicationFields(
        Long customerId,
        Long productId,
        String clientRequestId,
        BigDecimal amount,
        Integer termMonths,
        String purpose,
        String purposeDesc,
        BigDecimal income,
        String incomeType) {
}
