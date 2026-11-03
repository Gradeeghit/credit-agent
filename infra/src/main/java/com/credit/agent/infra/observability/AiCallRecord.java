package com.credit.agent.infra.observability;

/**
 * 一次模型调用的计量记录(H3 的原始数据)。
 * 业务含义: 单笔审批成本必须能按 trace_id 聚合出来, 所以每次调用都要留一条:
 * 用的什么模型、吃了多少 prompt / completion token、花了多久、属于哪类调用。
 * 注意: cost 不在本类计算 —— 金额由 domain 的计价函数确定性算出, 不在这里用 double 乘单价。
 * <p>
 * roundNo 的意义: 带 Function Calling 的一次 {@code ask()} 在供应商侧是 <b>N 次</b>模型调用
 * (每轮都要重新发送完整历史), 每轮各计一次费。只记最后一轮会系统性低估成本,
 * 因此网关按轮各记一条, roundNo 就是这一轮在本次 ask 里的序号(从 1 起)。
 *
 * @param traceId          全链路追踪 ID, 成本聚合的唯一维度
 * @param model            模型标识, 取不到时填 unknown
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param latencyMs        调用耗时(毫秒)
 * @param kind             调用类型, 决定用哪组单价; chat 与 embedding 的单价不同
 * @param roundNo          本次 ask 内的轮次, 从 1 起; 无工具时恒为 1
 */
public record AiCallRecord(
        String traceId,
        String model,
        long promptTokens,
        long completionTokens,
        long latencyMs,
        Kind kind,
        int roundNo) {

    /** 调用类型: chat 双向计价, embedding 只有输入。 */
    public enum Kind {
        /** 对话调用 */
        CHAT,
        /** 向量化调用(工具内部触发的检索也要按这个类型记账) */
        EMBEDDING
    }

    /** 兼容旧调用: 未声明类型时按 CHAT 处理。 */
    public AiCallRecord(String traceId, String model, long promptTokens, long completionTokens, long latencyMs) {
        this(traceId, model, promptTokens, completionTokens, latencyMs, Kind.CHAT, 1);
    }

    /** 兼容旧调用: 声明了类型但不是工具循环(embedding / 单轮 chat)时按第 1 轮处理。 */
    public AiCallRecord(String traceId, String model, long promptTokens, long completionTokens, long latencyMs,
                        Kind kind) {
        this(traceId, model, promptTokens, completionTokens, latencyMs, kind, 1);
    }

    public AiCallRecord {
        if (roundNo < 1) {
            roundNo = 1;
        }
    }
}
