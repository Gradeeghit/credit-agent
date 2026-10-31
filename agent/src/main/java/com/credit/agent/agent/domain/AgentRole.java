package com.credit.agent.agent.domain;

/**
 * 子 Agent 的材料域角色(F3)。
 * 业务含义: 按材料域拆分是控幻觉的核心手段 —— 每个域只看到自己那份证据,
 * 就不可能把"流水月均 2 万"和"收入证明 3 万"混在一起编理由, 冲突只能由 Supervisor 显式识别。
 * 取值与 approval_step.agent_role 的 CHECK 约束一致。
 */
public enum AgentRole {

    /** 材料核验: 身份证/收入证明/用途说明是否齐全自洽 */
    MATERIAL,

    /** 征信分析: 逾期、查询频次、负债 */
    CREDIT,

    /** 流水分析: 收支与稳定性 */
    STATEMENT,

    /** 制度检索: 条款引用 */
    POLICY,

    /** 汇总: 只做汇聚与冲突标注, 不单独取证 */
    SUPERVISOR
}
