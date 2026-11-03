package com.credit.agent.rag.retrieval;

import com.credit.agent.rag.retrieval.bm25.Bm25Retriever;
import com.credit.agent.rag.retrieval.rerank.Reranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 混合检索入口(C3)。
 * 业务含义: 同一批语料按 VECTOR / BM25 / HYBRID 三种模式召回, 便于在评测集上对比;
 * HYBRID 模式下两路各取候选后由 {@link RrfFusion} 按排名融合, 返回的 score 是 RRF 分(只用于排序,
 * 与余弦相似度、BM25 分都不同量纲)。
 * <p>
 * 降级: 任意一路抛异常(embedding 服务不可用 / 库超时)时只记警告并丢弃该路, 用另一路的结果继续,
 * 两路都失败才返回空列表 —— 检索不该因为一条通路挂掉就让整单审批失败。
 */
@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** 融合结果里的来源标识, 也用于排查某条命中是靠哪一路进来的 */
    static final String SOURCE_VECTOR = "vector";

    static final String SOURCE_BM25 = "bm25";

    private final VectorRetriever vectorRetriever;
    private final Bm25Retriever bm25Retriever;
    private final Reranker reranker;
    private final int rrfK;
    private final int candidateLimit;

    /** 单测用: 不接重排(重排为 null 时 rerank 请求自动退化)。 */
    public HybridRetriever(VectorRetriever vectorRetriever, Bm25Retriever bm25Retriever,
                           @Value("${credit-agent.retrieval.rrf-k:60}") int rrfK,
                           @Value("${credit-agent.retrieval.candidate-limit:20}") int candidateLimit) {
        this(vectorRetriever, bm25Retriever, null, rrfK, candidateLimit);
    }

    /** 容器用: 有 {@link Reranker} 才能提供 C4 重排; 没有时该能力自动关闭。 */
    @Autowired
    public HybridRetriever(VectorRetriever vectorRetriever, Bm25Retriever bm25Retriever, Reranker reranker,
                           @Value("${credit-agent.retrieval.rrf-k:60}") int rrfK,
                           @Value("${credit-agent.retrieval.candidate-limit:20}") int candidateLimit) {
        this.vectorRetriever = vectorRetriever;
        this.bm25Retriever = bm25Retriever;
        this.reranker = reranker;
        this.rrfK = rrfK;
        this.candidateLimit = candidateLimit;
    }

    /**
     * 按指定模式召回切片(不重排)。
     *
     * @param query   问题或关键词
     * @param limit   返回条数
     * @param docType 限定材料类型(制度检索传 POLICY_DOC), 优先级高于 docId
     * @param docId   限定单份文档
     * @param mode    检索模式, 为空按 HYBRID
     */
    public List<RetrievedChunk> search(String query, int limit, String docType, Long docId, SearchMode mode) {
        return search(query, limit, docType, docId, mode, false);
    }

    /**
     * 按指定模式召回, 可选 LLM 重排(C4)。
     * 业务含义: 重排需要"先宽召回再精排", 所以开启时召回数放大到 candidate-limit,
     * 由 {@link Reranker} 按语义相关性重排后只返回前 limit 条;
     * 没有可用的 Reranker 或重排失败时, 行为与不重排完全一致(降级不影响可用性)。
     *
     * @param rerank 是否对召回结果重排
     */
    public List<RetrievedChunk> search(String query, int limit, String docType, Long docId, SearchMode mode,
                                       boolean rerank) {
        SearchMode effective = mode == null ? SearchMode.HYBRID : mode;
        boolean needRerank = rerank && reranker != null;
        int recall = needRerank ? Math.max(candidateLimit, limit) : limit;
        List<RetrievedChunk> hits = switch (effective) {
            case VECTOR -> vectorSearch(query, recall, docType, docId);
            case BM25 -> bm25Retriever.search(query, recall, docType, docId);
            case HYBRID -> hybrid(query, recall, docType, docId);
        };
        if (!needRerank) {
            return hits;
        }
        return reranker.rerank(query, hits, limit);
    }

    private List<RetrievedChunk> hybrid(String query, int limit, String docType, Long docId) {
        int candidates = Math.max(candidateLimit, limit);
        List<RetrievedChunk> vectorHits = safely(
                () -> vectorSearch(query, candidates, docType, docId), SOURCE_VECTOR);
        List<RetrievedChunk> bm25Hits = safely(
                () -> bm25Retriever.search(query, candidates, docType, docId), SOURCE_BM25);
        if (vectorHits.isEmpty() && bm25Hits.isEmpty()) {
            return List.of();
        }

        Map<Long, RetrievedChunk> byId = new HashMap<>();
        vectorHits.forEach(hit -> byId.putIfAbsent(hit.chunkId(), hit));
        bm25Hits.forEach(hit -> byId.putIfAbsent(hit.chunkId(), hit));

        Map<String, List<Long>> ranked = new LinkedHashMap<>();
        ranked.put(SOURCE_VECTOR, vectorHits.stream().map(RetrievedChunk::chunkId).toList());
        ranked.put(SOURCE_BM25, bm25Hits.stream().map(RetrievedChunk::chunkId).toList());

        List<RrfFusion.FusedHit> fused = RrfFusion.fuse(ranked, rrfK, limit);
        List<RetrievedChunk> results = new ArrayList<>(fused.size());
        for (RrfFusion.FusedHit hit : fused) {
            RetrievedChunk chunk = byId.get(hit.chunkId());
            if (chunk != null) {
                results.add(withScore(chunk, hit.score()));
            }
        }
        return List.copyOf(results);
    }

    /** 把 RRF 分写回结果: 两路的原始分只用于各自内部排序, 融合后对外统一用 RRF 分。 */
    private static RetrievedChunk withScore(RetrievedChunk chunk, double score) {
        return new RetrievedChunk(chunk.chunkId(), chunk.docId(), chunk.docName(), chunk.section(),
                chunk.content(), chunk.pageStart(), chunk.pageEnd(), score);
    }

    /** 向量路: docType 优先(制度检索必须收口), 否则按 docId 或全库。 */
    private List<RetrievedChunk> vectorSearch(String query, int limit, String docType, Long docId) {
        if (docType != null && !docType.isBlank()) {
            return vectorRetriever.searchByDocType(query, limit, docType);
        }
        return vectorRetriever.search(query, limit, docId);
    }

    private List<RetrievedChunk> safely(Supplier<List<RetrievedChunk>> call, String source) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            log.warn("{} 召回失败, 降级为单路结果: {}", source, e.getMessage());
            return List.of();
        }
    }
}
