package com.credit.agent.api.web;

/**
 * 统一响应体。
 * 业务含义: 所有接口对外只返回 code / message / data 三段, 前端无需按接口区分解析方式。
 *
 * @param code    业务码, 成功为 OK, 失败为具体错误码
 * @param message 人读说明
 * @param data    业务数据, 失败时为 null
 */
public record ApiResponse<T>(String code, String message, T data) {

    /** 成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    /** 失败响应, 用于异常兜底。 */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
