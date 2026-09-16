package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.product.dto.CreateProductRequest;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;

    /**
     * 创建 ProductController 实例。
     *
     * @param productService 商品业务服务
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 查询业务数据列表。
     *
     * @param keyword 商品搜索关键词
     * @param categoryId 分类主键
     * @param saleType 发售Type参数
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sort 排序方式
     * @param page 页码
     * @param size 每页数量
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductEntity>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String saleType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(productService.findOnSale(
                keyword, categoryId, saleType, minPrice, maxPrice, sort, page, size));
    }

    /**
     * 查询业务详情。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductEntity> detail(@PathVariable Long id) {
        return ApiResponse.success(productService.findDetail(id));
    }

    /**
     * 创建文创商品。
     *
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductEntity>> create(@Valid @RequestBody CreateProductRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                productService.create(request, authentication == null ? null : authentication.getName())));
    }
}
