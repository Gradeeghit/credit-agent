package com.credit.agent.rag.chunk;

/**
 * 一个切片及其引用回链信息(B3)。
 * 业务含义: 切片是检索与引用的最小单位, 报告里写 [文件名 p.12-13] 时,
 * 文件名来自文档名, 页码由 pageStart/pageEnd 渲染, charStart 用于回原文核对。
 *
 * @param text      切片正文, 是引用展示与检索打分的真相源
 * @param pageStart 起始页码
 * @param pageEnd   结束页码, 跨页时大于 pageStart
 * @param section   所属章节标题; 首个标题之前的内容为 null
 * @param charStart 在整篇文档文本中的起始字符偏移
 */
public record Chunk(String text, int pageStart, int pageEnd, String section, int charStart) {
}
