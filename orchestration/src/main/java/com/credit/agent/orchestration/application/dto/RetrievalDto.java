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
     * @param mode    检索模式: VECTOR / BM25 / HYBRID, 不传默认 HYBRID
     * @param rerank  是否对召回结果做 LLM 重排(C4); 不传按全局开关
     *                {@code credit-agent.retrieval.rerank.enabled}(默认关闭)处理
     */
    public record SearchRequest(String query, Integer limit, Long docId, String docType, String mode, Boolean rerank) {

        /** 兼容旧调用: 不传 mode 与 rerank 时按默认处理。 */
        public SearchRequest(String query, Integer limit, Long docId, String docType) {
            this(query, limit, docId, docType, null, null);
        }

        /** 兼容 C3 调用: 只指定模式。 */
        public SearchRequest(String query, Integer limit, Long docId, String docType, String mode) {
            this(query, limit, docId, docType, mode, null);
        }
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
