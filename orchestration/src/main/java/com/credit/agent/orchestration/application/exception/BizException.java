package com.credit.agent.orchestration.application.exception;

/**
 * 业务异常。
 * 业务含义: 携带对外可返回的错误码与 HTTP 语义, 由 api 层统一异常处理转成结构化错误体;
 * 业务代码只抛本异常, 不直接依赖 Web 层类型。
 */
public class BizException extends RuntimeException {

    /** 错误码, 机器可读, 如 PRODUCT_NOT_FOUND */
    private final String code;

    /** HTTP 状态码, 由 api 层直接使用 */
    private final int status;

    public BizException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** 参数或业务前置条件不满足, 返回 400。 */
    public static BizException badRequest(String code, String message) {
        return new BizException(code, 400, message);
    }

    /** 资源不存在, 返回 404。 */
    public static BizException notFound(String code, String message) {
        return new BizException(code, 404, message);
    }

    /** 与当前状态冲突(如已受理的申请单不允许修改), 返回 409。 */
    public static BizException conflict(String code, String message) {
        return new BizException(code, 409, message);
    }

    /** 文件体积超限(A2: 单文件 > 10MB), 返回 413。 */
    public static BizException payloadTooLarge(String code, String message) {
        return new BizException(code, 413, message);
    }

    /** 媒体类型不受支持(A2: 非 PDF 材料), 返回 415。 */
    public static BizException unsupportedMediaType(String code, String message) {
        return new BizException(code, 415, message);
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
