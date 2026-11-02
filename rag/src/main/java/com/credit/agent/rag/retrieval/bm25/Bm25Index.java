package com.credit.agent.rag.retrieval.bm25;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BM25 索引(C1)。
 * 业务含义: 把一批切片建成"词 → 文档频次"的统计视图, 再按问题给每篇切片打相关性分;
 * 它只依赖词序列, 不调用模型、不花钱, 是混合检索里与向量互补的一路。
 * <p>
 * 打分口径(2026-11-02 本人拍板): IDF 采用 <b>Lucene 版</b>(恒非负), k1=1.5、b=0.75;
 * 词频饱和与长度归一化按标准 BM25 实现。
 * <pre>
 *   IDF(Lucene 版, 保证非负): ln(1 + (N - df + 0.5) / (df + 0.5))
 *   IDF(原始版, 可能为负):    ln((N - df + 0.5) / (df + 0.5))
 *   score = Σ IDF(qi) · tf·(k1+1) / ( tf + k1·(1 - b + b·|d|/avgdl) )
 * </pre>
 * 单测里已按 Lucene 版算好期望值; 若选原始版, 改 {@code Bm25IndexTest} 里两个数值即可。
 */
public final class Bm25Index {

    private final List<Bm25Document> documents;
    private final Map<String, Integer> documentFrequency;
    private final double averageLength;
    private final Bm25Params params;

    private Bm25Index(List<Bm25Document> documents, Map<String, Integer> documentFrequency,
                      double averageLength, Bm25Params params) {
        this.documents = documents;
        this.documentFrequency = documentFrequency;
        this.averageLength = averageLength;
        this.params = params;
    }

    /**
     * 建立索引: 统计文档频次(df)与平均文档长度(avgdl)。
     * 业务含义: df 决定 IDF(越少见越重要), avgdl 是长度归一化的基准;
     * 同一篇文档里重复出现多次只算一次 df, 否则长文档会把常见词压成"稀有词"。
     */
    public static Bm25Index build(List<Bm25Document> documents, Bm25Params params) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("语料不能为空");
        }
        if (params == null) {
            throw new IllegalArgumentException("BM25 参数不能为空");
        }
        Map<String, Integer> df = new HashMap<>();
        long totalLength = 0;
        for (Bm25Document document : documents) {
            Set<String> seen = new HashSet<>(document.tokens());
            for (String term : seen) {
                df.merge(term, 1, Integer::sum);
            }
            totalLength += document.length();
        }
        double avgdl = (double) totalLength / documents.size();
        return new Bm25Index(List.copyOf(documents), Map.copyOf(df), avgdl, params);
    }

    /** 语料文档数 N。 */
    public int documentCount() {
        return documents.size();
    }

    /** 平均文档长度 avgdl。 */
    public double averageLength() {
        return averageLength;
    }

    /** 某个词的文档频次 df, 未出现返回 0。 */
    public int documentFrequency(String term) {
        return documentFrequency.getOrDefault(term, 0);
    }

    public Bm25Params params() {
        return params;
    }

    /**
     * 计算词的逆文档频率(Lucene 版, 恒非负)。
     * 业务含义: 越少见的词越能区分文档 —— "连三累六"这类术语的权重远高于"贷款";
     * 采用 Lucene 版而非论文原版, 因为原版在 df &gt; N/2 时会算出负数,
     * 让常见词出现在文档里反而倒扣分, 排序难以向审批人解释。
     * 公式: {@code ln(1 + (N - df + 0.5) / (df + 0.5))}
     *
     * @return 查询词不在语料里(df=0)时返回 0, 即该词不参与打分, 也不会放大其他词
     */
    public double idf(String term) {
        int df = documentFrequency(term);
        if (df <= 0) {
            return 0.0;
        }
        return Math.log(1.0 + (documents.size() - df + 0.5) / (df + 0.5));
    }

    /**
     * 计算一篇切片对一组查询词的相关性分数。
     * 业务含义: 逐查询词累加「全局权重(IDF) × 本片词频饱和项 × 长度归一化」;
     * 不含任何查询词的切片得 0 分, 由 {@link #search} 过滤掉。
     * <p>
     * 细节约定: 查询词按原样累加(重复词视为强调), 去重由调用方决定;
     * 词频在打分时现统计, 若数据量增大可改为建索引时预算。
     */
    public double score(Bm25Document document, List<String> queryTokens) {
        if (document == null || queryTokens == null || queryTokens.isEmpty()) {
            return 0.0;
        }
        double avgdl = averageLength > 0 ? averageLength : 1.0;
        double k1 = params.k1();
        double b = params.b();
        double lengthNorm = 1.0 - b + b * (document.length() / avgdl);

        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : document.tokens()) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        double total = 0.0;
        for (String queryToken : queryTokens) {
            Integer tf = termFrequency.get(queryToken);
            if (tf == null || tf == 0) {
                continue;
            }
            double saturated = (tf * (k1 + 1.0)) / (tf + k1 * lengthNorm);
            total += idf(queryToken) * saturated;
        }
        return total;
    }

    /**
     * 按分数降序返回 top-k。
     * 业务含义: 只保留分数大于 0 的命中(完全不含查询词的切片不该占位), 同分时保持原顺序以便复现。
     */
    public List<ScoredChunk> search(List<String> queryTokens, int limit) {
        if (queryTokens == null || queryTokens.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<ScoredChunk> hits = new ArrayList<>(documents.size());
        for (Bm25Document document : documents) {
            double value = score(document, queryTokens);
            if (value > 0) {
                hits.add(new ScoredChunk(document.chunkId(), value));
            }
        }
        hits.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits);
    }

    /** 一条命中: 切片主键 + BM25 分数(与余弦相似度不同量纲, 融合时只用排名)。 */
    public record ScoredChunk(long chunkId, double score) {
    }
}
