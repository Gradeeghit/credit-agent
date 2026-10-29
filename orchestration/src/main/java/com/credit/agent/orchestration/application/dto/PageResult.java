package com.credit.agent.orchestration.application.dto;

import java.util.List;

/**
 * 分页结果。
 * 业务含义: 对外分页契约; 刻意不暴露 MyBatis-Plus 的 Page 类型, 避免 ORM 类型泄漏到 api 层。
 *
 * @param total   命中总条数
 * @param page    当前页, 从 1 开始
 * @param size    每页条数
 * @param records 当前页数据
 */
public record PageResult<T>(long total, long page, long size, List<T> records) {

    /** 构造分页结果, records 为 null 时归一化为空集合。 */
    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        return new PageResult<>(total, page, size, records == null ? List.of() : records);
    }
}
