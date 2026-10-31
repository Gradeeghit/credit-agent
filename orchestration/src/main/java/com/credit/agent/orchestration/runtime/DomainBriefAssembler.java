package com.credit.agent.orchestration.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.agent.domain.DomainEvidence;
import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import com.credit.agent.infra.persistence.service.DocumentService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按材料域从库里装配子 Agent 上下文(F3 的数据来源, 关闭风险 19)。
 * 业务含义: 分域隔离不能靠调用方自觉 —— 这里按 document.doc_type 把切片分到 MATERIAL / CREDIT / STATEMENT,
 * 每个 DomainBrief 只装本域的切片; 只取 is_current = true 的文档与切片, 避免把作废材料喂给模型。
 * 确定性数值(客户申报收入)也从库里取, 不由模型生成。
 */
@Service
public class DomainBriefAssembler {

    /** doc_type → 域: 一张材料只归属一个域, 不允许多域共享 */
    private static final Map<AgentRole, Set<String>> ROLE_DOC_TYPES = Map.of(
            AgentRole.MATERIAL, Set.of(Document.DOC_TYPE_ID_CARD, Document.DOC_TYPE_INCOME_CERT,
                    Document.DOC_TYPE_LOAN_PURPOSE),
            AgentRole.CREDIT, Set.of(Document.DOC_TYPE_CREDIT_REPORT),
            AgentRole.STATEMENT, Set.of(Document.DOC_TYPE_BANK_STATEMENT));

    private static final Pattern DOC_NAME = Pattern.compile("\"docName\"\\s*:\\s*\"([^\"]+)\"");

    private final DocumentService documentService;
    private final DocumentChunkService chunkService;
    private final LoanApplicationService applicationService;

    public DomainBriefAssembler(DocumentService documentService, DocumentChunkService chunkService,
                                LoanApplicationService applicationService) {
        this.documentService = documentService;
        this.chunkService = chunkService;
        this.applicationService = applicationService;
    }

    /**
     * 装配一张申请单的三个业务域上下文。
     * 业务含义: 三个域都返回(某域无材料时证据为空), 让上游能显式看到"这一域没有证据", 而不是少一个域。
     *
     * @param applicationId 申请单 ID
     * @return 材料域 / 征信域 / 流水域 三个 brief
     * @throws BizException 申请单不存在(404)
     */
    public List<DomainBrief> assemble(Long applicationId) {
        LoanApplication application = applicationService.getById(applicationId);
        if (application == null) {
            throw BizException.notFound("APPLICATION_NOT_FOUND", "申请单不存在: " + applicationId);
        }
        List<Document> documents = documentService.list(Wrappers.lambdaQuery(Document.class)
                .eq(Document::getApplicationId, applicationId)
                .eq(Document::getIsCurrent, true));
        List<DocumentChunk> chunks = chunksOf(documents);
        Map<AgentRole, List<DomainEvidence>> buckets = bucket(documents, chunks);
        Map<String, BigDecimal> incomeFacts = incomeFacts(application);
        return List.of(
                DomainBrief.forMaterial(applicationId, buckets.get(AgentRole.MATERIAL), incomeFacts),
                DomainBrief.forCredit(applicationId, buckets.get(AgentRole.CREDIT)),
                DomainBrief.forStatement(applicationId, buckets.get(AgentRole.STATEMENT)));
    }

    /**
     * 装配制度域上下文(公共语料, 不挂申请单)。
     * 业务含义: 制度检索是全库视角, 取当前有效的政策文档切片并限流, 防止一次倒进太多内容。
     *
     * @param maxChunks 最多取多少切片, 必须为正
     * @return 制度域 brief
     */
    public DomainBrief assemblePolicy(int maxChunks) {
        if (maxChunks <= 0) {
            throw new IllegalArgumentException("maxChunks 必须为正");
        }
        List<Document> documents = documentService.list(Wrappers.lambdaQuery(Document.class)
                .eq(Document::getDocType, Document.DOC_TYPE_POLICY_DOC)
                .eq(Document::getIsCurrent, true));
        List<DomainEvidence> evidence = evidenceOf(documents, chunksOf(documents));
        return DomainBrief.forPolicy(evidence.size() > maxChunks ? evidence.subList(0, maxChunks) : evidence);
    }

    /** 只查这些文档下的当前有效切片; 文档为空时不查库。 */
    private List<DocumentChunk> chunksOf(List<Document> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<Long> docIds = documents.stream().map(Document::getId).toList();
        return chunkService.list(Wrappers.lambdaQuery(DocumentChunk.class)
                .in(DocumentChunk::getDocId, docIds)
                .eq(DocumentChunk::getIsCurrent, true)
                .orderByAsc(DocumentChunk::getId));
    }

    /** 按 doc_type 把切片分桶; docId 不在当前文档集合里的切片一律丢弃。 */
    private static Map<AgentRole, List<DomainEvidence>> bucket(List<Document> documents,
                                                               List<DocumentChunk> chunks) {
        Map<Long, Document> byId = new LinkedHashMap<>();
        documents.forEach(doc -> byId.put(doc.getId(), doc));
        Map<AgentRole, List<DomainEvidence>> buckets = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            Document document = byId.get(chunk.getDocId());
            if (document == null) {
                continue;
            }
            AgentRole role = roleOf(document.getDocType());
            if (role == null) {
                continue;
            }
            buckets.computeIfAbsent(role, key -> new ArrayList<>()).add(toEvidence(document, chunk));
        }
        return buckets;
    }

    private static List<DomainEvidence> evidenceOf(List<Document> documents, List<DocumentChunk> chunks) {
        Map<Long, Document> byId = new LinkedHashMap<>();
        documents.forEach(doc -> byId.put(doc.getId(), doc));
        List<DomainEvidence> evidence = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Document document = byId.get(chunk.getDocId());
            if (document != null) {
                evidence.add(toEvidence(document, chunk));
            }
        }
        return evidence;
    }

    /** 申报收入只认 MONTHLY 口径; YEARLY(经营贷)不做折算, 不塞事实。 */
    private static Map<String, BigDecimal> incomeFacts(LoanApplication application) {
        if (application.getIncome() == null
                || !LoanApplication.INCOME_TYPE_MONTHLY.equals(application.getIncomeType())) {
            return Map.of();
        }
        return Map.of(DomainAnswer.FACT_INCOME_MONTHLY, application.getIncome());
    }

    private static AgentRole roleOf(String docType) {
        return ROLE_DOC_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(docType))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static DomainEvidence toEvidence(Document document, DocumentChunk chunk) {
        return new DomainEvidence(chunk.getId(), document.getId(), docNameOf(document),
                chunk.getPageStart(), chunk.getPageEnd(), chunk.getContent());
    }

    /** 文档名优先取 metadata.docName, 取不到退化为文件名, 保证引用能渲染成人能认的名字。 */
    static String docNameOf(Document document) {
        String metadata = document.getMetadata();
        if (metadata != null) {
            Matcher matcher = DOC_NAME.matcher(metadata);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        String path = document.getFilePath();
        if (path == null || path.isBlank()) {
            return "文档-" + document.getId();
        }
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
