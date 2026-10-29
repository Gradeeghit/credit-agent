package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.DocumentAppService;
import com.credit.agent.orchestration.application.PolicyDocAppService;
import com.credit.agent.orchestration.application.dto.DocumentDto;
import com.credit.agent.orchestration.application.dto.PageResult;
import com.credit.agent.orchestration.application.dto.PolicyDocDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 材料接口(A2/A3/A4/B0)。
 * 业务含义: 登记材料、回写解析状态、重传换版本; 政策文档另走 multipart 上传并立刻入库切片。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentAppService documentAppService;
    private final PolicyDocAppService policyDocAppService;

    public DocumentController(DocumentAppService documentAppService, PolicyDocAppService policyDocAppService) {
        this.documentAppService = documentAppService;
        this.policyDocAppService = policyDocAppService;
    }

    /** 上传政策文档(制度/年报)并完成解析切片入库(B0)。 */
    @PostMapping(value = "/policy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PolicyDocDto.Result> uploadPolicy(@RequestPart("file") MultipartFile file)
            throws IOException {
        return ApiResponse.ok(policyDocAppService.uploadAndIngest(file.getOriginalFilename(), file.getBytes()));
    }

    /** 分页查询材料。 */
    @GetMapping
    public ApiResponse<PageResult<DocumentDto.View>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) Boolean isCurrent) {
        return ApiResponse.ok(documentAppService.page(page, size, applicationId, docType, isCurrent));
    }

    /** 查询某申请单当前有效的全部材料。 */
    @GetMapping("/current")
    public ApiResponse<List<DocumentDto.View>> listCurrent(@RequestParam Long applicationId) {
        return ApiResponse.ok(documentAppService.listCurrent(applicationId));
    }

    /** 按主键查询材料。 */
    @GetMapping("/{id}")
    public ApiResponse<DocumentDto.View> get(@PathVariable Long id) {
        return ApiResponse.ok(documentAppService.get(id));
    }

    /** 登记材料。 */
    @PostMapping
    public ApiResponse<DocumentDto.View> register(@RequestBody DocumentDto.RegisterRequest req) {
        return ApiResponse.ok(documentAppService.register(req));
    }

    /** 重传材料, 生成新版本并把旧版本置为失效。 */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<DocumentDto.View> resubmit(@PathVariable Long id,
                                                  @RequestBody DocumentDto.ResubmitRequest req) {
        return ApiResponse.ok(documentAppService.resubmit(id, req));
    }

    /** 回写解析状态(PENDING/PARSED/FAILED/UNSUPPORTED)。 */
    @PostMapping("/{id}/parse-status")
    public ApiResponse<DocumentDto.View> updateParseStatus(@PathVariable Long id,
                                                           @RequestBody DocumentDto.ParseStatusRequest req) {
        return ApiResponse.ok(documentAppService.updateParseStatus(id, req));
    }

    /** 作废材料(逻辑删除, 保留历史引用)。 */
    @PostMapping("/{id}/retire")
    public ApiResponse<Void> retire(@PathVariable Long id) {
        documentAppService.retire(id);
        return ApiResponse.ok(null);
    }
}
