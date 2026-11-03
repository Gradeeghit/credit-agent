package com.credit.agent.rag.retrieval.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 重排结果解析(C4): 把模型输出的 {@code {"order":[3,1,2]}} 转换成可用的下标序列。
 * 业务含义: 模型输出不可信 —— 可能带代码围栏、带解释、漏编号、编号越界或重复,
 * 解析器必须把这些全部消化成"一个合法的排列", 而不是抛异常让检索失败。
 * <p>
 * 容错策略(按顺序):
 * <ol>
 *   <li>只取首个 <code>{</code> 到末个 <code>{@code }}</code> 之间的内容, 天然忽略前后解释与 ``` 围栏;</li>
 *   <li>编号按 <b>1 起</b> 解释(与提示词一致), 越界/非正/重复一律丢弃;</li>
 *   <li>模型漏掉的编号按原顺序补到末尾 —— 宁可少排一条, 也不能丢结果;</li>
 *   <li>整体解析失败返回 {@code null}, 由调用方降级为原顺序。</li>
 * </ol>
 */
public final class RerankOrderParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RerankOrderParser() {
    }

    /**
     * 解析模型输出的重排顺序。
     *
     * @param text 模型原始输出
     * @param size 候选片段数量
     * @return 0 起的下标排列, 长度恒为 size; 解析失败返回 null
     */
    public static List<Integer> parse(String text, int size) {
        if (size <= 0) {
            return List.of();
        }
        String json = extractJsonObject(text);
        if (json == null) {
            return null;
        }
        try {
            JsonNode order = MAPPER.readTree(json).get("order");
            if (order == null || !order.isArray()) {
                return null;
            }
            Set<Integer> picked = new LinkedHashSet<>();
            for (JsonNode item : order) {
                int index = item.asInt(0) - 1;
                if (index >= 0 && index < size) {
                    picked.add(index);
                }
            }
            if (picked.isEmpty()) {
                return null;
            }
            // LinkedHashSet 天然去重且保持插入顺序, 这里只负责把漏掉的补到末尾
            for (int i = 0; i < size; i++) {
                picked.add(i);
            }
            return List.copyOf(picked);
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        }
    }

    /** 截取首个 '{' 到末个 '}' 之间的内容; 找不到合法区间返回 null。 */
    private static String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
