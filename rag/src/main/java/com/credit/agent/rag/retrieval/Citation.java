package com.credit.agent.rag.retrieval;

/**
 * 引用文本渲染(C6)。
 * 业务含义: 报告与工具回答都必须能落成 [文档名 p.12-13] 这种可核对形态;
 * 单页写 p.12, 跨页写 p.12-13, 页码缺失时只保留文档名 —— 宁可少信息, 不许编造页码。
 */
public final class Citation {

    private Citation() {
    }

    /**
     * 渲染引用串。
     *
     * @param docName   文档名, 为空时用「未知文档」占位
     * @param pageStart 起始页, 可为 null
     * @param pageEnd   结束页, 可为 null 或等于起始页
     * @return 形如 [2024年报.pdf p.12-13]
     */
    public static String render(String docName, Integer pageStart, Integer pageEnd) {
        String name = (docName == null || docName.isBlank()) ? "未知文档" : docName;
        if (pageStart == null) {
            return "[" + name + "]";
        }
        String pages = (pageEnd == null || pageEnd.equals(pageStart))
                ? "p." + pageStart
                : "p." + pageStart + "-" + pageEnd;
        return "[" + name + " " + pages + "]";
    }
}
