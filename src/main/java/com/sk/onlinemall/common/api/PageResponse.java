package com.sk.onlinemall.common.api;

import java.util.List;

/** A stable page envelope for list APIs. */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {

    /**
     * 根据分页数据和总数创建分页响应。
     *
     * @param items 当前页数据
     * @param total 数据总数
     * @param page 当前页码
     * @param size 每页数量
     * @param <T> 分页元素类型
     * @return 包含总页数的分页响应
     */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }
}
