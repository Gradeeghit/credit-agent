package com.credit.agent.orchestration.application;

import com.credit.agent.orchestration.application.dto.RetrievalDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.retrieval.Citation;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.VectorRetriever;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 制度检索应用服务(C2)。
 * 业务含义: 把问题变成向量并召回制度条款, 供 E3 searchPolicy 与调试使用;
 * 本类不调用 LLM, 只做参数校验与引用渲染, 避免检索链路上出现不可复现的输出。
 */
@Service
public class RetrievalAppService {

    /** 单次召回上限: 超过这个数对 LLM 的上下文预算没有意义, 只会推高成本 */
    private static final int MAX_LIMIT = 50;

    private static final int DEFAULT_LIMIT = 5;

    private final VectorRetriever vectorRetriever;

    public RetrievalAppService(VectorRetriever vectorRetriever) {
        this.vectorRetriever = vectorRetriever;
    }

    /**
     * 按问题检索切片, 返回带引用的命中列表。
     * 业务含义: 空问题不发起 embedding 调用(避免无意义计费与空 SQL 全表扫描)。
     */
    public List<RetrievalDto.Hit> search(RetrievalDto.SearchRequest req) {
        if (req == null || req.query() == null || req.query().isBlank()) {
            throw BizException.badRequest("QUERY_REQUIRED", "检索问题不能为空");
        }
        int limit = req.limit() == null ? DEFAULT_LIMIT : req.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw BizException.badRequest("LIMIT_OUT_OF_RANGE", "召回条数必须在 1-" + MAX_LIMIT + " 之间");
        }
        return vectorRetriever.search(req.query().trim(), limit, req.docId()).stream()
                .map(RetrievalAppService::toHit)
                .toList();
    }

    private static RetrievalDto.Hit toHit(RetrievedChunk chunk) {
        return new RetrievalDto.Hit(chunk.chunkId(), chunk.docId(), chunk.docName(), chunk.section(),
                chunk.content(), chunk.pageStart(), chunk.pageEnd(), chunk.score(),
                citation(chunk.docName(), chunk.pageStart(), chunk.pageEnd()));
    }

    /**
     * 渲染引用文本(PRD C6 格式)。
     * 业务含义: 单页为 [文档名 p.12], 跨页为 [文档名 p.12-13]; 页码缺失时只保留文档名,
     * 宁可信息少也不能编造页码。实现已下沉到 rag, 供 E3 Tool 共用同一份规则。
     */
    static String citation(String docName, Integer pageStart, Integer pageEnd) {
        return Citation.render(docName, pageStart, pageEnd);
    }
}
