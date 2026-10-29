package com.credit.agent.infra.persistence.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL jsonb 与 Java String 之间的类型处理器。
 * 业务含义: document.metadata / document_chunk.metadata / approval_report.content_json 存的是
 * 原始 JSON 文本, 由写入方自行序列化; 读取时原样返回, 避免 ORM 层丢失字段。
 * 技术要点: 写入用 setObject(..., Types.OTHER), 让 PG 驱动把字符串按 jsonb 传参,
 * 从而不在 infra 里依赖 PGobject(驱动是 runtime 作用域, 编译期不可见)。
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
