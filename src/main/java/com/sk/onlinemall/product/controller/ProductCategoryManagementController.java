package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.product.dto.CreateCategoryRequest;
import com.sk.onlinemall.product.dto.UpdateCategoryRequest;
import com.sk.onlinemall.product.model.ProductCategoryEntity;
import com.sk.onlinemall.product.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class ProductCategoryManagementController {
    private final ProductCategoryService categoryService;

    /**
     * 创建运营分类控制器。
     *
     * @param categoryService 分类业务服务
     */
    public ProductCategoryManagementController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询全部商品分类。
     *
     * @return 全部分类
     */
    @GetMapping
    public ApiResponse<List<ProductCategoryEntity>> list() {
        return ApiResponse.success(categoryService.findAll());
    }

    /**
     * 创建商品分类。
     *
     * @param request 创建请求
     * @return 新增分类
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductCategoryEntity>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(categoryService.create(request)));
    }

    /**
     * 更新商品分类。
     *
     * @param categoryId 分类主键
     * @param request 更新请求
     * @return 更新后的分类
     */
    @PutMapping("/{categoryId}")
    public ApiResponse<ProductCategoryEntity> update(@PathVariable Long categoryId,
                                                      @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success(categoryService.update(categoryId, request));
    }

    /**
     * 删除没有商品引用的分类。
     *
     * @param categoryId 分类主键
     * @return 空响应
     */
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.success(null);
    }
}
