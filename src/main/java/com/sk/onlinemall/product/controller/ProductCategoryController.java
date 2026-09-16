package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.product.dto.CreateCategoryRequest;
import com.sk.onlinemall.product.model.ProductCategoryEntity;
import com.sk.onlinemall.product.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class ProductCategoryController {
    private final ProductCategoryService categoryService;

    /**
     * 创建 ProductCategoryController 实例。
     *
     * @param categoryService 分类业务服务
     */
    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询业务数据列表。
     *
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<List<ProductCategoryEntity>> list() {
        return ApiResponse.success(categoryService.findActive());
    }

    /**
     * 创建商品分类。
     *
     * @param request 请求参数
     * @return 处理后的业务数据
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductCategoryEntity>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.create(request)));
    }
}
