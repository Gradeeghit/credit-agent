package com.credit.agent.orchestration.report;

/**
 * 报告"风险点"节的一条记录。
 * 业务含义: 风险点只描述"看到了什么风险、来自哪", 不下"是否放贷"的结论;
 * 无风险时也必须留一条 NONE 占位, 保证 G1 的"六节均非空"成立且不产生歧义。
 *
 * @param code        风险码, 如 INCOME_INCONSISTENT
 * @param description 人读描述
 * @param level       严重度: HIGH / MEDIUM / LOW
 */
public record RiskItem(String code, String description, String level) {

    public static final String LEVEL_HIGH = "HIGH";
    public static final String LEVEL_MEDIUM = "MEDIUM";
    public static final String LEVEL_LOW = "LOW";

    /** 占位码: 明确表示"本报告未发现显著风险" */
    public static final String CODE_NONE = "NONE";

    /** 域内异常但无跨域冲突时的风险码 */
    public static final String CODE_DOMAIN_ABNORMAL = "DOMAIN_ABNORMAL";

    /** AI 分析被终止(超预算/超步数/模型不可用)时的风险码 */
    public static final String CODE_AI_DEGRADED = "AI_DEGRADED";

    public RiskItem {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("风险码不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("风险描述不能为空");
        }
    }

    /** 是否为"无风险"占位项。 */
    public boolean isPlaceholder() {
        return CODE_NONE.equals(code);
    }
}
