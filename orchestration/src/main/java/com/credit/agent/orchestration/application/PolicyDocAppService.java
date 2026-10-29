package com.credit.agent.orchestration.application;

import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.orchestration.application.dto.DocumentDto;
import com.credit.agent.orchestration.application.dto.PolicyDocDto;
import com.credit.agent.orchestration.application.exception.BizException;
import com.credit.agent.rag.ingest.DocumentIngestService;
import com.credit.agent.rag.ingest.IngestResult;
import com.credit.agent.rag.ingest.MetadataJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * 政策文档上传入库应用服务(B0)。
 * 业务含义: 信审员上传制度/年报后, 系统自动走"落文件 → 登记 document → 解析切片 → 回写解析状态",
 * 让 E3 searchPolicy 有真实语料可检索; 本类不做任何金额计算, 也不调用 LLM。
 */
@Service
public class PolicyDocAppService {

    private static final Logger log = LoggerFactory.getLogger(PolicyDocAppService.class);

    /** A2 口径: 单文件上限 10MB */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DocumentAppService documentAppService;
    private final DocumentIngestService ingestService;
    private final Path uploadDir;

    public PolicyDocAppService(DocumentAppService documentAppService, DocumentIngestService ingestService,
                               @Value("${credit-agent.upload-dir:uploads}") String uploadDir) {
        this.documentAppService = documentAppService;
        this.ingestService = ingestService;
        this.uploadDir = Path.of(uploadDir);
    }

    /**
     * 上传并入库一份政策文档。
     * 业务含义: 入库成功后该文档的切片立刻可被制度检索命中; 入库失败也保留 document 记录以便追查与重传。
     *
     * @param fileName 原始文件名, 必须 .pdf 结尾
     * @param content  PDF 字节
     * @return 文档 ID、解析状态与切片数
     */
    public PolicyDocDto.Result uploadAndIngest(String fileName, byte[] content) {
        validate(fileName, content);
        Path saved = store(content);
        DocumentDto.View document = documentAppService.register(new DocumentDto.RegisterRequest(
                null, Document.DOC_TYPE_POLICY_DOC, saved.toString(), MetadataJson.documentMetadata(fileName)));
        IngestResult ingest = ingestService.ingest(document.id(), fileName, content);
        documentAppService.updateParseStatus(document.id(),
                new DocumentDto.ParseStatusRequest(ingest.parseStatus(), ingest.error()));
        log.info("政策文档入库: docId={}, docName={}, status={}, chunks={}",
                document.id(), fileName, ingest.parseStatus(), ingest.chunkCount());
        return new PolicyDocDto.Result(document.id(), fileName, ingest.parseStatus(),
                ingest.chunkCount(), ingest.error());
    }

    /** 校验扩展名、体积与 PDF 文件头; 不合规返回 415/413, 不进入落盘与解析。 */
    private static void validate(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw BizException.badRequest("FILE_NAME_REQUIRED", "文件名不能为空");
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw BizException.unsupportedMediaType("FILE_TYPE_NOT_PDF", "只支持 PDF 材料");
        }
        if (content == null || content.length == 0) {
            throw BizException.badRequest("FILE_EMPTY", "文件内容为空");
        }
        if (content.length > MAX_BYTES) {
            throw BizException.payloadTooLarge("FILE_TOO_LARGE", "单文件不得超过 10MB");
        }
        if (content.length < PDF_MAGIC.length || !startsWithPdfMagic(content)) {
            throw BizException.unsupportedMediaType("FILE_NOT_PDF_CONTENT", "文件内容不是 PDF");
        }
    }

    private static boolean startsWithPdfMagic(byte[] content) {
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** 落盘到配置目录, 文件名用 UUID 避免同名覆盖与路径穿越。 */
    private Path store(byte[] content) {
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(UUID.randomUUID() + ".pdf");
            Files.write(target, content);
            return target.toAbsolutePath();
        } catch (IOException e) {
            throw new UncheckedIOException("政策文档落盘失败", e);
        }
    }
}
