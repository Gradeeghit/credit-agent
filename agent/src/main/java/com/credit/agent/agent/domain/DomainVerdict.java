package com.credit.agent.agent.domain;

/**
 * 域内结论等级(F4 汇聚的唯一判据)。
 * 业务含义: Supervisor 不允许去正则解析模型自由文本 —— 先把文本折成枚举,
 * 汇聚逻辑只认枚举, 这样"是否需补材料/是否异常"是可判定、可单测的, 而不是拼关键词运气。
 */
public enum DomainVerdict {

    /** 本域未发现异常 */
    NORMAL,

    /** 本域发现异常/高风险信号, 支撑拒贷建议 */
    ABNORMAL,

    /** 材料缺失或无法判定, 需要客户补充 */
    NEED_MORE_INFO
}
