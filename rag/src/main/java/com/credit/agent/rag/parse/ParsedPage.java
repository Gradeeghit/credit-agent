package com.credit.agent.rag.parse;

/**
 * 单页文本快照。
 * 业务含义: 报告里每条引用 [文件名 p.12-13] 的页码来源, 页码必须是 PDF 物理页序, 从 1 开始;
 * charOffset 记录该页首字符在整篇文本中的偏移, 保证下游切片的 charStart 可回推原文位置。
 *
 * @param pageNo     物理页码, 从 1 开始
 * @param text       该页抽出的文本, 已做行尾空白清理; 影像件为空串
 * @param charOffset 该页首字符在整篇文本中的偏移
 */
public record ParsedPage(int pageNo, String text, int charOffset) {
}
