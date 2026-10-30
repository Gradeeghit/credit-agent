package com.credit.agent.api.controller;

import com.credit.agent.api.web.ApiResponse;
import com.credit.agent.orchestration.application.RetrievalAppService;
import com.credit.agent.orchestration.application.dto.RetrievalDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 制度检索接口(C2)。
 * 业务含义: 暴露向量召回能力, 供 E3 searchPolicy 与联调排查使用;
 * 只做检索, 不生成答案, 答案由 Agent 结合引用组织。
 */
@RestController
@RequestMapping("/api/rag")
public class RetrievalController {

    private final RetrievalAppService retrievalAppService;

    public RetrievalController(RetrievalAppService retrievalAppService) {
        this.retrievalAppService = retrievalAppService;
    }

    /** 按问题召回切片, 命中项自带 citation。 */
    @PostMapping("/search")
    public ApiResponse<List<RetrievalDto.Hit>> search(@RequestBody RetrievalDto.SearchRequest req) {
        return ApiResponse.ok(retrievalAppService.search(req));
    }
}
