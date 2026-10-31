package com.credit.agent.orchestration.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 报告内容的 JSON 序列化(G3: 落 approval_report.content_json)。
 * 业务含义: content_json 是报告的唯一真相源, 序列化失败必须立刻炸而不是写入半截内容,
 * 否则审批人看到的是一份残缺报告却毫无提示。
 * 金额字段为 BigDecimal, Jackson 会输出精确小数, 不走 double。
 */
public final class ReportContentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReportContentJson() {
    }

    /**
     * 序列化六节内容。
     *
     * @param content 六节结构
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败(理论上不会发生, 但绝不静默忽略)
     */
    public static String write(ReportContent content) {
        if (content == null) {
            throw new IllegalArgumentException("报告内容不能为空");
        }
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("报告内容序列化失败", e);
        }
    }
}
