package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.dto.UpdateProductRequest;
import com.sk.onlinemall.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductManagementController {
    private final ProductService productService;

    /**
     * 创建运营商品控制器。
     *
     * @param productService 商品业务服务
     */
    public ProductManagementController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 分页查询运营侧商品目录。
     *
     * @param keyword 商品搜索关键词
     * @param status 商品状态
     * @param page 页码
     * @param size 每页数量
     * @return 运营商品分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductEntity>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(productService.findForManagement(keyword, status, page, size));
    }

    /**
     * 更新商品资料和库存。
     *
     * @param productId 商品主键
     * @param request 更新请求
     * @return 更新后的商品
     */
    @PutMapping("/{productId}")
    public ApiResponse<ProductEntity> update(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.update(productId, request));
    }

    /**
     * 下架在售商品。
     *
     * @param productId 商品主键
     * @return 下架后的商品
     */
    @PostMapping("/{productId}/off-sale")
    public ApiResponse<ProductEntity> takeOffSale(@PathVariable Long productId) {
        return ApiResponse.success(productService.takeOffSale(productId));
    }

    /**
     * 重新上架已审核且未改动的商品。
     *
     * @param productId 商品主键
     * @return 上架后的商品
     */
    @PostMapping("/{productId}/on-sale")
    public ApiResponse<ProductEntity> putOnSale(@PathVariable Long productId) {
        return ApiResponse.success(productService.putOnSale(productId));
    }

    /**
     * 删除没有业务历史的非在售商品。
     *
     * @param productId 商品主键
     * @return 空响应
     */
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success(null);
    }
}
