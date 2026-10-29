package com.credit.agent.rag.ingest;

/**
 * 切片/文档元数据的 JSON 拼装。
 * 业务含义: 文档名、章节、字符偏移是报告引用回链的凭据, 必须原样落进 jsonb,
 * 所以这里的转义错了会导致引用对不上文档, 必须单测覆盖引号与反斜杠。
 * 技术说明: rag 不额外引入 JSON 库, 用最小手写转义; 只生成对象, 不解析。
 */
public final class MetadataJson {

    private MetadataJson() {
    }

    /**
     * 切片元数据: {"docId":1,"docName":"x","section":"第一章 总则","charStart":0}
     * section 允许为 null(首个标题之前的内容)。
     */
    public static String chunkMetadata(long docId, String docName, String section, int charStart) {
        return "{\"docId\":" + docId
                + ",\"docName\":" + quote(docName)
                + ",\"section\":" + (section == null ? "null" : quote(section))
                + ",\"charStart\":" + charStart + "}";
    }

    /** 文档元数据: {"docName":"2024年报.pdf"}, 供引用渲染取文件名。 */
    public static String documentMetadata(String docName) {
        return "{\"docName\":" + quote(docName) + "}";
    }

    /** 转成带引号的 JSON 字符串字面量; null 输出 null 字面量。 */
    public static String quote(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
