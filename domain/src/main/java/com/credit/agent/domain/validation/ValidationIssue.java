package com.credit.agent.domain.validation;

/**
 * 单条字段校验问题(D6)。
 * 业务含义: 错误必须能精确指到字段, 前端才能把提示挂到对应输入框,
 * 所以 field + code + message 三段缺一不可(PRD D6 验收要求错误体含 field 与 code)。
 *
 * @param field   出问题的字段名, 与请求体字段一致(如 amount)
 * @param code    机器可读错误码(如 AMOUNT_NOT_POSITIVE), 供前端做分支
 * @param message 人读说明, 直接展示给客户
 */
public record ValidationIssue(String field, String code, String message) {
}
