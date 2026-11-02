package com.credit.agent.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * C5 评测集加载器。
 * 业务含义: 评测语料与标注必须随代码一起进版本库(否则"上周 Recall 0.8"无法复现),
 * 所以放 test resources 并由本类读取; 语料是虚构的, 不对应任何真实监管文件。
 */
public record C5Dataset(List<EvalChunk> chunks, List<EvalQuery> queries) {

    private static final String CORPUS = "/eval/c5-corpus.json";
    private static final String QUERIES = "/eval/c5-queries.json";

    /** 真实条文语料: 《个人贷款管理办法》(2024年第3号), 一条一片, 共 52 片 */
    private static final String REAL_CORPUS = "/eval/c5-real-corpus.json";
    private static final String REAL_QUERIES = "/eval/c5-real-queries.json";

    public C5Dataset {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    /** 加载默认评测集(c5-corpus.json + c5-queries.json, 30 段虚构语料)。 */
    public static C5Dataset load() {
        return load(CORPUS, QUERIES);
    }

    /**
     * 加载真实条文评测集(《个人贷款管理办法》52 条 + 30 条标注)。
     * 业务含义: 虚构语料措辞规整、术语集中, 真实条文更接近线上检索场景, 用它复核结论是否仍然成立。
     */
    public static C5Dataset loadReal() {
        return load(REAL_CORPUS, REAL_QUERIES);
    }

    /** 加载指定资源, 便于做小语料的自测。 */
    public static C5Dataset load(String corpusPath, String queryPath) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode corpus = read(mapper, corpusPath);
        JsonNode queryRoot = read(mapper, queryPath);
        return new C5Dataset(chunksOf(corpus), queriesOf(queryRoot));
    }

    private static JsonNode read(ObjectMapper mapper, String path) {
        try (InputStream in = C5Dataset.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("评测资源不存在: " + path);
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("评测资源读取失败: " + path, e);
        }
    }

    private static List<EvalChunk> chunksOf(JsonNode root) {
        List<EvalChunk> chunks = new ArrayList<>();
        for (JsonNode node : root.path("chunks")) {
            chunks.add(new EvalChunk(node.path("chunkId").asLong(), node.path("docName").asText(),
                    node.path("page").asInt(), node.path("section").asText(), node.path("content").asText()));
        }
        return chunks;
    }

    private static List<EvalQuery> queriesOf(JsonNode root) {
        List<EvalQuery> queries = new ArrayList<>();
        for (JsonNode node : root.path("queries")) {
            List<Long> gold = new ArrayList<>();
            for (JsonNode id : node.path("gold")) {
                gold.add(id.asLong());
            }
            queries.add(new EvalQuery(node.path("id").asText(), node.path("query").asText(),
                    gold, node.path("tag").asText()));
        }
        return queries;
    }
}
