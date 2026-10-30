package com.credit.agent.domain.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 申请单字段校验(D6)。
 * 业务含义: 必填、金额正数、期限正数、用途长度、收入口径、证件号位数与校验位,
 * 全部是确定性规则, 必须在进入规则引擎之前拦掉, 不能指望 LLM 发现"金额填成负数"。
 * 一次返回全部问题而不是遇到第一个就抛, 前端才能一次性标红所有输入框。
 */
public final class ApplicationFieldValidator {

    public static final int PURPOSE_MAX_LENGTH = 32;
    public static final int PURPOSE_DESC_MAX_LENGTH = 512;
    public static final int CLIENT_REQUEST_ID_MAX_LENGTH = 64;

    /** 金额最多两位小数, 与 NUMERIC(18,2) 对齐 */
    private static final int AMOUNT_MAX_SCALE = 2;

    private static final Set<String> INCOME_TYPES = Set.of("MONTHLY", "YEARLY");

    private static final int ID_CARD_LENGTH = 18;
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private ApplicationFieldValidator() {
    }

    /**
     * 校验一笔申请提交。
     * 业务含义: 返回空列表表示可以进入额度与月供计算; 非空表示必须打回客户补正。
     */
    public static List<ValidationIssue> validate(ApplicationFields fields) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (fields == null) {
            issues.add(new ValidationIssue("body", "BODY_REQUIRED", "请求体不能为空"));
            return List.copyOf(issues);
        }
        if (fields.customerId() == null) {
            issues.add(new ValidationIssue("customerId", "CUSTOMER_REQUIRED", "申请人不能为空"));
        }
        if (fields.productId() == null) {
            issues.add(new ValidationIssue("productId", "PRODUCT_REQUIRED", "产品不能为空"));
        }
        validateAmount(fields.amount(), issues);
        if (fields.termMonths() == null || fields.termMonths() <= 0) {
            issues.add(new ValidationIssue("termMonths", "TERM_INVALID", "申请期限必须是正整数月"));
        }
        validatePurpose(fields.purpose(), fields.purposeDesc(), issues);
        if (fields.income() != null && fields.income().compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(new ValidationIssue("income", "INCOME_NOT_POSITIVE", "收入必须大于 0"));
        }
        if (!isBlank(fields.incomeType())
                && !INCOME_TYPES.contains(fields.incomeType().trim().toUpperCase(Locale.ROOT))) {
            issues.add(new ValidationIssue("incomeType", "INCOME_TYPE_INVALID", "收入口径只能是 MONTHLY 或 YEARLY"));
        }
        if (fields.clientRequestId() != null && fields.clientRequestId().length() > CLIENT_REQUEST_ID_MAX_LENGTH) {
            issues.add(new ValidationIssue("clientRequestId", "CLIENT_REQUEST_ID_TOO_LONG",
                    "幂等键长度不能超过 " + CLIENT_REQUEST_ID_MAX_LENGTH));
        }
        return List.copyOf(issues);
    }

    /**
     * 校验身份证号: 位数、字符、校验位。
     * 业务含义: 17 位或缺校验位的证件号在受理阶段就该拦下, 不能等到征信查询才失败。
     */
    public static List<ValidationIssue> validateIdCard(String idCardNo) {
        if (isBlank(idCardNo)) {
            return List.of(new ValidationIssue("idCardNo", "ID_CARD_REQUIRED", "身份证号不能为空"));
        }
        String value = idCardNo.trim().toUpperCase(Locale.ROOT);
        if (value.length() != ID_CARD_LENGTH) {
            return List.of(new ValidationIssue("idCardNo", "ID_CARD_LENGTH_INVALID", "身份证号必须是 18 位"));
        }
        for (int i = 0; i < ID_CARD_LENGTH - 1; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return List.of(new ValidationIssue("idCardNo", "ID_CARD_FORMAT_INVALID", "身份证号前 17 位必须是数字"));
            }
        }
        int sum = 0;
        for (int i = 0; i < ID_CARD_LENGTH - 1; i++) {
            sum += (value.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }
        if (value.charAt(ID_CARD_LENGTH - 1) != ID_CARD_CHECK_CODES[sum % 11]) {
            return List.of(new ValidationIssue("idCardNo", "ID_CARD_CHECKSUM_INVALID", "身份证号校验位不正确"));
        }
        return List.of();
    }

    private static void validateAmount(BigDecimal amount, List<ValidationIssue> issues) {
        if (amount == null) {
            issues.add(new ValidationIssue("amount", "AMOUNT_REQUIRED", "申请金额不能为空"));
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(new ValidationIssue("amount", "AMOUNT_NOT_POSITIVE", "申请金额必须大于 0"));
        }
        if (amount.scale() > AMOUNT_MAX_SCALE) {
            issues.add(new ValidationIssue("amount", "AMOUNT_SCALE_INVALID", "申请金额最多保留 2 位小数"));
        }
    }

    private static void validatePurpose(String purpose, String purposeDesc, List<ValidationIssue> issues) {
        if (isBlank(purpose)) {
            issues.add(new ValidationIssue("purpose", "PURPOSE_REQUIRED", "贷款用途不能为空"));
        } else if (purpose.trim().length() > PURPOSE_MAX_LENGTH) {
            issues.add(new ValidationIssue("purpose", "PURPOSE_TOO_LONG",
                    "贷款用途长度不能超过 " + PURPOSE_MAX_LENGTH));
        }
        if (purposeDesc != null && purposeDesc.length() > PURPOSE_DESC_MAX_LENGTH) {
            issues.add(new ValidationIssue("purposeDesc", "PURPOSE_DESC_TOO_LONG",
                    "用途说明长度不能超过 " + PURPOSE_DESC_MAX_LENGTH));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
