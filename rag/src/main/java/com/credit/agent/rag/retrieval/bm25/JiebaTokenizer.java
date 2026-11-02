package com.credit.agent.rag.retrieval.bm25;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 基于 jieba 的中文分词器。
 * 业务含义: 制度条款里有大量中文短语与英文缩写(LPR/P1), 用 SEARCH 模式切词对短 query 更友好;
 * 统一小写后再统计, 避免 "LPR" 与 "lpr" 被算成两个词。
 */
@Component
public class JiebaTokenizer implements Tokenizer {

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return segmenter.process(text, JiebaSegmenter.SegMode.SEARCH).stream()
                .map(token -> token.word)
                .filter(this::isMeaningful)
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    /** 过滤空白与纯标点: 这类 token 对相关性没有贡献, 只会稀释词频统计。 */
    private boolean isMeaningful(String word) {
        return word != null && !word.isBlank()
                && word.chars().anyMatch(ch -> Character.isLetterOrDigit(ch));
    }
}
