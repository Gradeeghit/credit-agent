package com.credit.agent.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D6 字段校验单测: 合法提交通过、金额非法、期限非法、证件号位数与校验位。 */
class ApplicationFieldValidatorTest {

    @Test
    @DisplayName("合法提交返回空列表")
    void validSubmissionShouldPass() {
        assertEquals(List.of(), ApplicationFieldValidator.validate(validFields()));
    }

    @Test
    @DisplayName("金额为空/为负/三位小数各返回一条带 field 与 code 的问题")
    void invalidAmountShouldBeReported() {
        List<ValidationIssue> issues = ApplicationFieldValidator.validate(new ApplicationFields(
                1L, 1L, null, new BigDecimal("-100"), 12, "消费", null, new BigDecimal("20000"), "MONTHLY"));

        assertEquals(1, issues.size());
        assertEquals("amount", issues.get(0).field());
        assertEquals("AMOUNT_NOT_POSITIVE", issues.get(0).code());

        List<ValidationIssue> missing = ApplicationFieldValidator.validate(new ApplicationFields(
                1L, 1L, null, null, 12, "消费", null, null, "MONTHLY"));
        assertEquals("AMOUNT_REQUIRED", missing.get(0).code());

        List<ValidationIssue> scale = ApplicationFieldValidator.validate(new ApplicationFields(
                1L, 1L, null, new BigDecimal("100.001"), 12, "消费", null, null, "MONTHLY"));
        assertEquals("AMOUNT_SCALE_INVALID", scale.get(0).code());
    }

    @Test
    @DisplayName("期限为 0 与用途为空都会被拦下, 且一次返回两条")
    void invalidTermAndPurposeShouldBeReported() {
        List<ValidationIssue> issues = ApplicationFieldValidator.validate(new ApplicationFields(
                1L, 1L, null, new BigDecimal("10000"), 0, " ", null, null, "MONTHLY"));

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> "TERM_INVALID".equals(i.code())));
        assertTrue(issues.stream().anyMatch(i -> "PURPOSE_REQUIRED".equals(i.code())));
    }

    @Test
    @DisplayName("17 位证件号被拒(PRD D6 验收点), 校验位错误的 18 位号也被拒")
    void invalidIdCardShouldBeReported() {
        assertEquals("ID_CARD_LENGTH_INVALID",
                ApplicationFieldValidator.validateIdCard("11010119900307123").get(0).code());
        assertEquals("ID_CARD_CHECKSUM_INVALID",
                ApplicationFieldValidator.validateIdCard("110101199003071234").get(0).code());
    }

    @Test
    @DisplayName("校验位正确的 18 位证件号通过")
    void validIdCardShouldPass() {
        assertEquals(List.of(), ApplicationFieldValidator.validateIdCard("110101199003071233"));
    }

    private static ApplicationFields validFields() {
        return new ApplicationFields(1L, 1L, "req-1", new BigDecimal("50000.00"), 12,
                "消费", "装修", new BigDecimal("20000"), "MONTHLY");
    }
}
