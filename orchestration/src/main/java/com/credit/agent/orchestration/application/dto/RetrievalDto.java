package com.credit.agent.orchestration.application.dto;

/**
 * 向量检索对外契约(C2)。
 * 业务含义: 供 Tool(E3 searchPolicy)与调试入口共用; 每条命中都带 citation,
 * 保证回答能落到 [文档名 p.12-13] 这种可核对的引用形态。
 */
public final class RetrievalDto {

    private RetrievalDto() {
    }

    /**
     * 检索入参。
     *
     * @param query   自然语言问题或关键词
     * @param limit   召回条数, 不传默认 5, 允许 1-50
     * @param docId   限定文档, 不传表示不限
     * @param docType 限定材料类型(如 POLICY_DOC), 优先级高于 docId; 制度检索必须传
     */
    public record SearchRequest(String query, Integer limit, Long docId, String docType) {
    }

    /** 命中结果, citation 已按 PRD 格式拼好, 前端与报告可直接使用。 */
    public record Hit(
            long chunkId,
            long docId,
            String docName,
            String section,
            String content,
            Integer pageStart,
            Integer pageEnd,
            double score,
            String citation) {
    }
}
