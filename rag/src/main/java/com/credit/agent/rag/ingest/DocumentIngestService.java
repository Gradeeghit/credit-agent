package com.credit.agent.rag.ingest;

import com.credit.agent.infra.persistence.entity.Document;
import com.credit.agent.infra.persistence.entity.DocumentChunk;
import com.credit.agent.infra.persistence.service.DocumentChunkService;
import com.credit.agent.rag.chunk.Chunk;
import com.credit.agent.rag.chunk.ChunkingStrategy;
import com.credit.agent.rag.parse.ParsedPage;
import com.credit.agent.rag.parse.PdfParseResult;
import com.credit.agent.rag.parse.PdfPageTextExtractor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档入库链路: 解析 → 切分 → 切片落库(B0/B1/B2/B3 的公共实现)。
 * 业务含义: 制度年报与客户材料走同一条链路, 入库失败必须给出可读原因而不是静默失败;
 * 本类只产文本切片, 不生成向量(向量化属 B5, 未解锁前 embedding 列保持 NULL)。
 */
@Service
public class DocumentIngestService {

    private final PdfPageTextExtractor extractor;
    private final ChunkingStrategy chunkingStrategy;
    private final DocumentChunkService chunkService;

    public DocumentIngestService(PdfPageTextExtractor extractor, ChunkingStrategy chunkingStrategy,
                                 DocumentChunkService chunkService) {
        this.extractor = extractor;
        this.chunkingStrategy = chunkingStrategy;
        this.chunkService = chunkService;
    }

    /**
     * 解析并切分一份 PDF, 把切片写入 document_chunk。
     * 业务含义: 返回的 parseStatus 决定该文档能否被检索引用; 只有 PARSED 才有切片可召回。
     *
     * @param docId    document 表主键, 切片通过它回链文档
     * @param docName  文档名, 落进 metadata 供 [文件名 p.N] 引用渲染
     * @param pdfBytes PDF 原始字节
     * @return 解析状态 + 实际入库切片数
     */
    public IngestResult ingest(Long docId, String docName, byte[] pdfBytes) {
        PdfParseResult parsed = extractor.extract(pdfBytes);
        if (!parsed.isParsed()) {
            return new IngestResult(parsed.parseStatus(), 0, parsed.error());
        }
        List<ParsedPage> pages = parsed.pages();
        List<Chunk> chunks = chunkingStrategy.split(pages);
        if (chunks.isEmpty()) {
            return new IngestResult(Document.PARSE_FAILED, 0, "解析成功但切分结果为空");
        }
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            entities.add(toEntity(docId, docName, chunk));
        }
        chunkService.saveBatch(entities);
        return new IngestResult(Document.PARSE_PARSED, entities.size(), null);
    }

    /** 切片转实体: isCurrent 恒为 true(重传时由上层把旧切片置 false), metadata 记录引用回链字段。 */
    private static DocumentChunk toEntity(Long docId, String docName, Chunk chunk) {
        DocumentChunk entity = new DocumentChunk();
        entity.setDocId(docId);
        entity.setContent(chunk.text());
        entity.setPageStart(chunk.pageStart());
        entity.setPageEnd(chunk.pageEnd());
        entity.setIsCurrent(true);
        entity.setMetadata(MetadataJson.chunkMetadata(docId, docName, chunk.section(), chunk.charStart()));
        return entity;
    }
}
