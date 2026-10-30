package com.credit.agent.orchestration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import com.credit.agent.infra.persistence.service.DocumentService;
import com.credit.agent.infra.persistence.service.LoanApplicationService;
import com.credit.agent.orchestration.application.dto.DocumentDto;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 材料/政策文档应用服务(A2 登记 / A3 解析状态 / A4 重传)。
 * 业务含义: 材料是审批的输入证据, 重传不覆盖历史而是换版本;
 * 这里只落库, 不做文件解析与切片(属 rag 模块)。
 */
@Service
public class DocumentAppService {

    private static final Set<String> DOC_TYPES = Set.of(Document.DOC_TYPE_ID_CARD,
            Document.DOC_TYPE_INCOME_CERT, Document.DOC_TYPE_CREDIT_REPORT,
            Document.DOC_TYPE_BANK_STATEMENT, Document.DOC_TYPE_LOAN_PURPOSE,
            Document.DOC_TYPE_POLICY_DOC);

    private static final Set<String> PARSE_STATUS = Set.of(Document.PARSE_PENDING,
            Document.PARSE_PARSED, Document.PARSE_FAILED, Document.PARSE_UNSUPPORTED);

    private final DocumentService documentService;
    private final LoanApplicationService applicationService;
    private final DocumentChunkService chunkService;

    public DocumentAppService(DocumentService documentService, LoanApplicationService applicationService,
                              DocumentChunkService chunkService) {
        this.documentService = documentService;
        this.applicationService = applicationService;
        this.chunkService = chunkService;
    }

    /** 分页查询材料, 可按申请单/类型/是否当前有效过滤。 */
    public PageResult<DocumentDto.View> page(long page, long size, Long applicationId,
                                             String docType, Boolean isCurrent) {
        var wrapper = Wrappers.lambdaQuery(Document.class)
                .eq(applicationId != null, Document::getApplicationId, applicationId)
                .eq(docType != null && !docType.isBlank(), Document::getDocType, docType)
                .eq(isCurrent != null, Document::getIsCurrent, isCurrent)
                .orderByDesc(Document::getCreatedAt);
        Page<Document> result = documentService.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(DocumentAppService::toView).toList());
    }

    /** 查询某申请单下当前有效的全部材料, 供材料完整性预检使用。 */
    public List<DocumentDto.View> listCurrent(Long applicationId) {
        return documentService.list(Wrappers.lambdaQuery(Document.class)
                        .eq(Document::getApplicationId, applicationId)
                        .eq(Document::getIsCurrent, true)
                        .orderByAsc(Document::getDocType))
                .stream().map(DocumentAppService::toView).toList();
    }

    /** 按主键查询材料。 */
    public DocumentDto.View get(Long id) {
        return toView(require(id));
    }

    /** 登记材料: 首次登记 version=1 且为当前有效, 解析状态为 PENDING。 */
    public DocumentDto.View register(DocumentDto.RegisterRequest req) {
        validateType(req.docType(), req.applicationId());
        if (req.filePath() == null || req.filePath().isBlank()) {
            throw BizException.badRequest("FILE_PATH_REQUIRED", "文件路径不能为空");
        }
        Document entity = new Document();
        entity.setApplicationId(req.applicationId());
        entity.setDocType(req.docType());
        entity.setFilePath(req.filePath());
        entity.setVersion(1);
        entity.setIsCurrent(true);
        entity.setParseStatus(Document.PARSE_PENDING);
        entity.setMetadata(req.metadata());
        documentService.save(entity);
        return toView(require(entity.getId()));
    }

    /**
     * 重传材料: 新版本 version+1 置为有效并重置解析状态, 旧版本置为失效;
     * 同时把旧版本的切片全部置为失效, 否则检索与报告仍会引用已作废材料(A4)。
     * 三步必须同事务, 任何一步失败都不能留下"两份都有效"的中间态。
     */
    @Transactional
    public DocumentDto.View resubmit(Long id, DocumentDto.ResubmitRequest req) {
        Document old = require(id);
        if (req.filePath() == null || req.filePath().isBlank()) {
            throw BizException.badRequest("FILE_PATH_REQUIRED", "文件路径不能为空");
        }
        Document fresh = new Document();
        fresh.setApplicationId(old.getApplicationId());
        fresh.setDocType(old.getDocType());
        fresh.setFilePath(req.filePath());
        fresh.setVersion(old.getVersion() + 1);
        fresh.setIsCurrent(true);
        fresh.setParseStatus(Document.PARSE_PENDING);
        fresh.setMetadata(req.metadata());
        documentService.save(fresh);
        Document patch = new Document();
        patch.setId(old.getId());
        patch.setIsCurrent(false);
        documentService.updateById(patch);
        retireChunks(old.getId());
        return toView(require(fresh.getId()));
    }

    /** 回写解析状态(A3); 失败必须带原因, 便于信审员判断是否需要重传。 */
    public DocumentDto.View updateParseStatus(Long id, DocumentDto.ParseStatusRequest req) {
        if (req.parseStatus() == null || !PARSE_STATUS.contains(req.parseStatus())) {
            throw BizException.badRequest("PARSE_STATUS_INVALID",
                    "解析状态只能是 PENDING/PARSED/FAILED/UNSUPPORTED");
        }
        if (Document.PARSE_FAILED.equals(req.parseStatus())
                && (req.parseError() == null || req.parseError().isBlank())) {
            throw BizException.badRequest("PARSE_ERROR_REQUIRED", "解析失败必须给出原因");
        }
        Document patch = new Document();
        patch.setId(require(id).getId());
        patch.setParseStatus(req.parseStatus());
        patch.setParseError(req.parseError());
        documentService.updateById(patch);
        return toView(require(id));
    }

    /** 作废材料: 置 isCurrent=false 并连带失效其切片, 不物理删除, 保证历史报告引用可回链。 */
    @Transactional
    public void retire(Long id) {
        Document doc = require(id);
        Document patch = new Document();
        patch.setId(id);
        patch.setIsCurrent(false);
        documentService.updateById(patch);
        retireChunks(doc.getId());
    }

    /**
     * 让某个 document 版本下的全部切片失效。
     * 业务含义: 切片带 doc_id 且每个版本是独立的 document 记录, 因此只影响被替换的那一版;
     * 已失效的切片不再重复更新, 避免无意义写放大。
     * 实现说明: 这里用显式列名的 UpdateWrapper 而非 lambda 版, 后者依赖 MyBatis-Plus 的
     * TableInfo 缓存, 在无 Spring 容器的单测里会抛 "can not find lambda cache"。
     */
    private void retireChunks(Long docId) {
        chunkService.update(Wrappers.<DocumentChunk>update()
                .eq("doc_id", docId)
                .eq("is_current", true)
                .set("is_current", false));
    }

    private void validateType(String docType, Long applicationId) {
        if (docType == null || !DOC_TYPES.contains(docType)) {
            throw BizException.badRequest("DOC_TYPE_INVALID", "材料类型不合法: " + docType);
        }
        boolean policy = Document.DOC_TYPE_POLICY_DOC.equals(docType);
        if (policy && applicationId != null) {
            throw BizException.badRequest("POLICY_DOC_NO_APPLICATION", "政策文档不允许挂在申请单下");
        }
        if (!policy && applicationId == null) {
            throw BizException.badRequest("APPLICATION_REQUIRED", "业务材料必须挂在申请单下");
        }
        if (applicationId != null && applicationService.getById(applicationId) == null) {
            throw BizException.badRequest("APPLICATION_NOT_FOUND", "申请单不存在: " + applicationId);
        }
    }

    private Document require(Long id) {
        Document entity = documentService.getById(id);
        if (entity == null) {
            throw BizException.notFound("DOCUMENT_NOT_FOUND", "材料不存在: " + id);
        }
        return entity;
    }

    private static DocumentDto.View toView(Document d) {
        return new DocumentDto.View(d.getId(), d.getApplicationId(), d.getDocType(), d.getFilePath(),
                d.getVersion(), d.getIsCurrent(), d.getParseStatus(), d.getParseError(), d.getMetadata(),
                d.getCreatedAt());
    }
}
