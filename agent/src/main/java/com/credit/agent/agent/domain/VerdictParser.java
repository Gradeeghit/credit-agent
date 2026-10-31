package com.credit.agent.agent.domain;

import java.util.List;

/**
 * 模型自由文本 → 域结论枚举。
 * 业务含义: 模型输出必须被折叠成有限状态, 否则"异常"写成"存在一定风险"就漏判了;
 * 这里只做关键词归类, 抽取不到明确结论时按「需补材料」处理, 宁可多转人工也不放过。
 */
public final class VerdictParser {

    /** 缺材料类关键词: 命中即需补, 优先级最高 */
    private static final List<String> NEED_MORE = List.of("需补", "待补", "缺少", "缺失", "无法判定", "不可用");

    /** 异常类关键词 */
    private static final List<String> ABNORMAL = List.of("异常", "高风险", "不符", "不一致", "不予", "拒绝", "违规");

    private VerdictParser() {
    }

    /**
     * 归类模型输出。
     *
     * @param text 模型输出文本, 可为空
     * @return 结论等级; 空文本或无明确结论时返回 {@link DomainVerdict#NEED_MORE_INFO}
     */
    public static DomainVerdict parse(String text) {
        if (text == null || text.isBlank()) {
            return DomainVerdict.NEED_MORE_INFO;
        }
        if (containsAny(text, NEED_MORE)) {
            return DomainVerdict.NEED_MORE_INFO;
        }
        if (containsAny(text, ABNORMAL)) {
            return DomainVerdict.ABNORMAL;
        }
        return DomainVerdict.NORMAL;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}
