package com.credit.agent.rag.chunk;

import com.credit.agent.rag.parse.ParsedPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 标题层级切分(B2): 按 第X章 / 一、 / 1.1 断章, 超长章节再滑窗。
 * 业务含义: 制度文档的章节是天然语义边界, 按标题切能让引用落到具体条款;
 * 单块上限 800 字、滑窗重叠 150 字, 保证召回时既不丢上下文也不撑爆上下文窗口。
 * 纯函数实现, 不依赖 DB / LLM, 可独立单测。
 */
@Component
public class HeadingChunkingStrategy implements ChunkingStrategy {

    /** 单块最大字符数, 超出触发滑窗 */
    static final int MAX_CHARS = 800;

    /** 滑窗重叠字符数, 防止跨块语义被切断 */
    static final int OVERLAP = 150;

    /** 标题行长度上限, 超过视为正文, 避免把编号段落误判成标题 */
    private static final int HEADING_MAX_LENGTH = 40;

    /** 只认"章/节/篇"为切分点; "第X条"是章内条款, 切到条会碎成几百块, 不采纳 */
    private static final Pattern CHAPTER = Pattern.compile("^第[一二三四五六七八九十百千0-9]+[章节篇]");
    private static final Pattern CN_ITEM = Pattern.compile("^[一二三四五六七八九十]+、");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+(\\.\\d+)*[ 、.．]");

    /**
     * 按标题切分, 超长章节滑窗。
     * 业务含义: 保证每个切片都能回答"这段来自哪一章、哪几页", 无引用能力的切片没有业务价值。
     *
     * @param pages 逐页文本, 页码从 1 开始
     * @return 顺序切片; 全空白输入返回空列表
     */
    @Override
    public List<Chunk> split(List<ParsedPage> pages) {
        List<Chunk> chunks = new ArrayList<>();
        if (pages == null || pages.isEmpty()) {
            return chunks;
        }
        List<Line> buffer = new ArrayList<>();
        String section = null;
        for (ParsedPage page : pages) {
            int localOffset = 0;
            for (String rawLine : page.text().split("\n", -1)) {
                int start = page.charOffset() + localOffset;
                localOffset += rawLine.length() + 1;
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (isHeading(line)) {
                    emit(chunks, section, buffer);
                    buffer.clear();
                    section = line;
                }
                buffer.add(new Line(line, page.pageNo(), start));
            }
        }
        emit(chunks, section, buffer);
        return chunks;
    }

    /** 判断一行是否为章节标题: 命中三种编号规则且长度不超过上限。 */
    private static boolean isHeading(String line) {
        if (line.length() > HEADING_MAX_LENGTH) {
            return false;
        }
        return CHAPTER.matcher(line).find()
                || CN_ITEM.matcher(line).find()
                || NUMBERED.matcher(line).find();
    }

    /** 把一个章节的累积行切块并追加到结果; 章节内无内容则跳过。 */
    private static void emit(List<Chunk> out, String section, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int[] lineStarts = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            lineStarts[i] = sb.length();
            sb.append(lines.get(i).text());
        }
        String text = sb.toString();
        int absStart = lines.get(0).startOffset();
        if (text.length() <= MAX_CHARS) {
            out.add(chunkOf(text, 0, text.length(), section, absStart, lines, lineStarts));
            return;
        }
        int step = MAX_CHARS - OVERLAP;
        int start = 0;
        int emitted = 0;
        while (text.length() - start > MAX_CHARS) {
            out.add(chunkOf(text, start, start + MAX_CHARS, section, absStart, lines, lineStarts));
            emitted++;
            start += step;
        }
        if (start >= text.length()) {
            return;
        }
        int remaining = text.length() - start;
        int tailStart = remaining < step && emitted > 0 ? text.length() - step : start;
        out.add(chunkOf(text, tailStart, text.length(), section, absStart, lines, lineStarts));
    }

    /** 构造切片, 并用行偏移把首尾字符映射回物理页码。 */
    private static Chunk chunkOf(String text, int from, int to, String section, int absStart,
                                 List<Line> lines, int[] lineStarts) {
        int pageStart = pageAt(lineStarts, lines, from);
        int pageEnd = pageAt(lineStarts, lines, to - 1);
        return new Chunk(text.substring(from, to), pageStart, pageEnd, section, absStart + from);
    }

    /** 二分查找位置所属的行, 返回该行的物理页码。 */
    private static int pageAt(int[] lineStarts, List<Line> lines, int position) {
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts[mid] <= position) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return lines.get(low).pageNo();
    }

    /** 切分过程中的中间行: 保留页码与在整篇文本中的绝对偏移。 */
    private record Line(String text, int pageNo, int startOffset) {
    }
}
