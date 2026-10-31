package com.credit.agent.tool.contract;

/**
 * 统一 Tool 返回契约(E5)。
 * 业务含义: LLM 看到的必须是"可读的错误对象"而不是异常堆栈 ——
 * 参数错就告诉它哪个参数错, 超时就告诉它结果不完整, 它会据此改写参数或降级作答, 而不是崩溃重试。
 * 三态语义: OK=正常; ERROR=调用不可用(参数非法/资源不存在/内部失败), data 为 null;
 * DEGRADED=调用超时或检索降级, 返回空集合并带 reason, 业务流程可继续。
 *
 * @param status  OK / ERROR / DEGRADED
 * @param code    OK 时为 null; 其余为机器可读错误码, 如 PARAM_INVALID / NOT_FOUND / TOOL_TIMEOUT
 * @param message 人读说明, 直接给 LLM 看
 * @param data    业务数据, 仅 OK 与 DEGRADED 时可能有值
 */
public record ToolResponse<T>(String status, String code, String message, T data) {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_DEGRADED = "DEGRADED";

    /** 正常返回。 */
    public static <T> ToolResponse<T> ok(T data) {
        return new ToolResponse<>(STATUS_OK, null, null, data);
    }

    /** 调用失败: 不抛异常给 LLM, 用错误码说明原因。 */
    public static <T> ToolResponse<T> error(String code, String message) {
        return new ToolResponse<>(STATUS_ERROR, code, message, null);
    }

    /** 超时或降级: 带上已拿到的部分结果(reason 与 approval_step.failure_reason 枚举对齐)。 */
    public static <T> ToolResponse<T> degraded(String reason, String message, T data) {
        return new ToolResponse<>(STATUS_DEGRADED, reason, message, data);
    }

    /** 是否正常返回; 降级不算成功, 调用方必须自行判断是否继续。 */
    public boolean isOk() {
        return STATUS_OK.equals(status);
    }
}
