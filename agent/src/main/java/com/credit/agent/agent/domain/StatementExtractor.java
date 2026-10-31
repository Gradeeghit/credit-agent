package com.credit.agent.agent.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 从模型输出中抽取流水入账明细(A 部分的"读取")。
 * 业务含义: 模型只做**抽取**(把 PDF 里的每笔入账金额读出来), 汇总与月均由
 * {@link com.credit.agent.domain.loan.StatementMonthlyAverage} 计算 —— 抽取是 AI, 算钱是代码。
 * 容错: 模型可能包 ```json 围栏、可能少字段、可能给字符串金额, 一律尽量解析;
 * 解析不出就返回空列表, 让上层按"无数据"处理, 绝不抛异常打断审批。
 */
public final class StatementExtractor {

    /** 单次最多接受的明细条数, 防止模型输出超长 JSON 把内存打爆 */
    private static final int MAX_TRANSACTIONS = 5_000;

    /**
     * 关键配置: 小数一律按 BigDecimal 解析。
     * 默认 Jackson 会把 12000.00 解析成 double(变成 12000.0), 金额一旦走浮点就失去精度保证(D7 的红线)。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private StatementExtractor() {
    }

    /**
     * 抽取并按月汇总入账金额。
     *
     * @param llmText 模型输出, 期望形如 {"transactions":[{"date":"2026-01-05","amount":12000.00}]}
     * @return 每个自然月的入账合计, 按月份升序; 无有效数据时返回空列表
     */
    public static List<BigDecimal> monthlyInflows(String llmText) {
        JsonNode root = parseObject(llmText);
        if (root == null) {
            return List.of();
        }
        JsonNode transactions = root.path("transactions");
        if (!transactions.isArray()) {
            return List.of();
        }
        Map<String, BigDecimal> monthly = new TreeMap<>();
        int accepted = 0;
        for (JsonNode item : transactions) {
            if (accepted >= MAX_TRANSACTIONS) {
                break;
            }
            String month = monthOf(item.path("date").asText(null));
            BigDecimal amount = amountOf(item.path("amount"));
            if (month == null || amount == null || amount.signum() <= 0) {
                continue;
            }
            monthly.merge(month, amount, BigDecimal::add);
            accepted++;
        }
        return new ArrayList<>(monthly.values());
    }

    /** 从文本里抠出第一个 JSON 对象并解析; 失败返回 null。 */
    private static JsonNode parseObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 "yyyy-MM": 支持 2026-01-05 / 2026/01/05 / 2026-01。 */
    private static String monthOf(String date) {
        if (date == null || date.length() < 7) {
            return null;
        }
        String normalized = date.replace('/', '-');
        if (normalized.charAt(4) != '-') {
            return null;
        }
        return normalized.substring(0, 7);
    }

    /** 金额兼容数字与字符串两种写法, 统一 2 位小数; 非法返回 null。 */
    private static BigDecimal amountOf(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
            }
            if (node.isTextual()) {
                return new BigDecimal(node.asText().trim()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
