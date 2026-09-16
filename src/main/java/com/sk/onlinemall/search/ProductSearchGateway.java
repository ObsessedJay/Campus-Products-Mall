package com.sk.onlinemall.search;

import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.product.model.ProductEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定义商品搜索引擎访问边界。
 */
public interface ProductSearchGateway {
    /**
     * 搜索在售商品。
     *
     * @param keyword 关键词
     * @param categoryId 分类主键
     * @param saleType 发售方式
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sort 排序方式
     * @param page 页码
     * @param size 每页数量
     * @return 搜索分页结果
     */
    PageResponse<ProductEntity> search(String keyword, Long categoryId, String saleType,
                                       BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                       int page, int size);

    /**
     * 使用当前商品快照重建索引。
     *
     * @param products 商品快照
     */
    void rebuild(List<ProductEntity> products);
}
