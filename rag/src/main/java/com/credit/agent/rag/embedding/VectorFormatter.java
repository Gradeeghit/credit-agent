package com.credit.agent.rag.embedding;

/**
 * 向量与 pgvector 文本字面量互转。
 * 业务含义: document_chunk.embedding 是 vector(1024) 类型, MyBatis-Plus 不认,
 * 所以写入时把 float[] 转成 "[0.1,0.2,...]" 再用 CAST(? AS vector) 交给 PG 解析。
 * 注意: 不用 PGobject, 因为 PG 驱动在 infra 是 runtime 作用域, 编译期不可见。
 */
public final class VectorFormatter {

    private VectorFormatter() {
    }

    /**
     * 把向量转成 pgvector 可解析的字面量, 形如 {@code [0.1,0.2,0.3]}。
     * 业务含义: NaN / Infinity 会被 PG 拒绝并污染检索结果, 必须在写入前拦截。
     *
     * @throws IllegalArgumentException 向量为空或含非有限值时抛出
     */
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("向量第 " + i + " 维不是有限值: " + value);
            }
            sb.append(Float.toString(value));
        }
        return sb.append(']').toString();
    }
}
