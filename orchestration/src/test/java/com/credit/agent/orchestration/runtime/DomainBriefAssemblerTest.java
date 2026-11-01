package com.credit.agent.orchestration.runtime;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.credit.agent.agent.domain.AgentRole;
import com.credit.agent.agent.domain.DomainAnswer;
import com.credit.agent.agent.domain.DomainBrief;
import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import com.credit.agent.infra.persistence.entity.LoanApplication;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import com.credit.agent.infra.persistence.service.DocumentService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.retrieval.RetrievedChunk;
import com.credit.agent.rag.retrieval.VectorRetriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 域上下文装配单测: doc_type 分桶、跨文档切片丢弃、申报收入注入。 */
class DomainBriefAssemblerTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentChunkService chunkService = mock(DocumentChunkService.class);
    private final LoanApplicationService applicationService = mock(LoanApplicationService.class);
    private final VectorRetriever vectorRetriever = mock(VectorRetriever.class);
    private final DomainBriefAssembler assembler =
            new DomainBriefAssembler(documentService, chunkService, applicationService, vectorRetriever);

    @Test
    @DisplayName("按 doc_type 分桶: 材料 2 条 / 征信 1 条 / 流水 1 条, 外来 docId 的切片被丢弃")
    void bucketsByDocType() {
        givenApplication("30000.00", LoanApplication.INCOME_TYPE_MONTHLY);
        when(documentService.list(any(Wrapper.class))).thenReturn(List.of(
                document(1L, Document.DOC_TYPE_ID_CARD, "{\"docName\":\"身份证.pdf\"}", "uploads/uuid1.pdf"),
                document(2L, Document.DOC_TYPE_INCOME_CERT, null, "uploads\\收入证明.pdf"),
                document(3L, Document.DOC_TYPE_CREDIT_REPORT, null, "uploads/uuid3.pdf"),
                document(4L, Document.DOC_TYPE_BANK_STATEMENT, null, "uploads/uuid4.pdf")));
        when(chunkService.list(any(Wrapper.class))).thenReturn(List.of(
                chunk(11L, 1L), chunk(12L, 2L), chunk(13L, 3L), chunk(14L, 4L), chunk(99L, 77L)));

        List<DomainBrief> briefs = assembler.assemble(9L);

        assertEquals(4, briefs.size(), "三个业务域 + 一个制度域");
        DomainBrief material = byRole(briefs, AgentRole.MATERIAL);
        assertEquals(2, material.materials().size());
        assertTrue(material.creditReports().isEmpty());
        assertTrue(material.statements().isEmpty());
        assertEquals("身份证.pdf", material.materials().get(0).docName(), "metadata.docName 优先");
        assertEquals("收入证明.pdf", material.materials().get(1).docName(), "无 metadata 时取文件名");
        assertEquals(1, byRole(briefs, AgentRole.CREDIT).creditReports().size());
        assertEquals(1, byRole(briefs, AgentRole.STATEMENT).statements().size());
    }

    @Test
    @DisplayName("制度域: 按申请单拼 query 检索 POLICY_DOC 切片, 只装入检索命中项")
    void policyBriefUsesRetrieval() {
        LoanApplication application = givenApplication("20000.00", LoanApplication.INCOME_TYPE_MONTHLY);
        application.setAmount(new BigDecimal("200000.00"));
        application.setTermMonths(12);
        application.setPurpose("装修");
        when(documentService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chunkService.list(any(Wrapper.class))).thenReturn(List.of());
        when(vectorRetriever.searchByDocType(anyString(), anyInt(), anyString())).thenReturn(List.of(
                new RetrievedChunk(51L, 8L, "2024年报.pdf", "第三章 准入", "逾期 3 次以上不予准入", 12, 13, 0.88)));

        DomainBrief policy = byRole(assembler.assemble(9L), AgentRole.POLICY);

        assertEquals(1, policy.policyChunks().size());
        assertEquals("2024年报.pdf", policy.policyChunks().get(0).docName());
        assertEquals(51L, policy.policyChunks().get(0).chunkId());
        assertTrue(DomainBriefAssembler.policyQuery(application).contains("用途装修"));
        assertTrue(DomainBriefAssembler.policyQuery(application).contains("金额200000.00元"));
    }

    @Test
    @DisplayName("制度检索失败: 只降级为空气证据, 不影响另外三个域")
    void policyRetrievalFailureDegrades() {
        givenApplication("20000.00", LoanApplication.INCOME_TYPE_MONTHLY);
        when(documentService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chunkService.list(any(Wrapper.class))).thenReturn(List.of());
        when(vectorRetriever.searchByDocType(anyString(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("embedding down"));

        List<DomainBrief> briefs = assembler.assemble(9L);

        assertEquals(4, briefs.size());
        assertTrue(byRole(briefs, AgentRole.POLICY).evidence().isEmpty());
        assertTrue(briefs.stream().anyMatch(b -> b.role() == AgentRole.MATERIAL));
    }

    @Test
    @DisplayName("申报月收入进材料域 facts; YEARLY 口径不塞事实(不做折算)")
    void injectsMonthlyIncomeOnly() {
        givenApplication("30000.00", LoanApplication.INCOME_TYPE_MONTHLY);
        when(documentService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chunkService.list(any(Wrapper.class))).thenReturn(List.of());

        DomainBrief material = byRole(assembler.assemble(9L), AgentRole.MATERIAL);
        assertEquals(new BigDecimal("30000.00"), material.fact(DomainAnswer.FACT_INCOME_MONTHLY));

        givenApplication("480000.00", LoanApplication.INCOME_TYPE_YEARLY);
        DomainBrief yearly = byRole(assembler.assemble(9L), AgentRole.MATERIAL);
        assertTrue(yearly.facts().isEmpty());
    }

    @Test
    @DisplayName("申请单不存在 → 404, 不返回空 brief")
    void missingApplicationFails() {
        when(applicationService.getById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> assembler.assemble(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    @DisplayName("制度域: 只装政策文档切片并限流")
    void assemblesPolicyWithLimit() {
        when(documentService.list(any(Wrapper.class))).thenReturn(List.of(
                document(1L, Document.DOC_TYPE_POLICY_DOC, "{\"docName\":\"2024年报.pdf\"}", "uploads/uuid.pdf")));
        when(chunkService.list(any(Wrapper.class))).thenReturn(List.of(
                chunk(21L, 1L), chunk(22L, 1L), chunk(23L, 1L)));

        DomainBrief brief = assembler.assemblePolicy(2);

        assertEquals(AgentRole.POLICY, brief.role());
        assertEquals(2, brief.policyChunks().size());
        assertEquals("2024年报.pdf", brief.policyChunks().get(0).docName());
        assertThrows(IllegalArgumentException.class, () -> assembler.assemblePolicy(0));
    }

    private LoanApplication givenApplication(String income, String incomeType) {
        LoanApplication application = new LoanApplication();
        application.setId(9L);
        application.setIncome(new BigDecimal(income));
        application.setIncomeType(incomeType);
        when(applicationService.getById(9L)).thenReturn(application);
        return application;
    }

    private static DomainBrief byRole(List<DomainBrief> briefs, AgentRole role) {
        return briefs.stream().filter(b -> b.role() == role).findFirst().orElseThrow();
    }

    private static Document document(Long id, String docType, String metadata, String filePath) {
        Document document = new Document();
        document.setId(id);
        document.setDocType(docType);
        document.setMetadata(metadata);
        document.setFilePath(filePath);
        document.setIsCurrent(true);
        return document;
    }

    private static DocumentChunk chunk(Long id, Long docId) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setContent("正文" + id);
        chunk.setPageStart(1);
        chunk.setPageEnd(1);
        chunk.setIsCurrent(true);
        return chunk;
    }
}
