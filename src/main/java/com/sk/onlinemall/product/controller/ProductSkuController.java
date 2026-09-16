package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.product.dto.UpsertProductSkuRequest;
import com.sk.onlinemall.product.model.ProductSkuEntity;
import com.sk.onlinemall.product.service.ProductSkuService;
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
@RequestMapping("/api/v1")
public class ProductSkuController {
    private final ProductSkuService skuService;

    /**
     * 创建商品规格控制器。
     *
     * @param skuService 商品规格服务
     */
    public ProductSkuController(ProductSkuService skuService) {
        this.skuService = skuService;
    }

    /**
     * 查询公开商品规格。
     *
     * @param productId 商品主键
     * @return 启用规格列表
     */
    @GetMapping("/products/{productId}/skus")
    public ApiResponse<List<ProductSkuEntity>> listPublic(@PathVariable Long productId) {
        return ApiResponse.success(skuService.findPublic(productId));
    }

    /**
     * 查询运营端商品规格。
     *
     * @param productId 商品主键
     * @return 全部规格列表
     */
    @GetMapping("/admin/products/{productId}/skus")
    public ApiResponse<List<ProductSkuEntity>> listForManagement(@PathVariable Long productId) {
        return ApiResponse.success(skuService.findForManagement(productId));
    }

    /**
     * 新增商品规格。
     *
     * @param productId 商品主键
     * @param request 规格请求
     * @return 新增规格
     */
    @PostMapping("/admin/products/{productId}/skus")
    public ResponseEntity<ApiResponse<ProductSkuEntity>> create(
            @PathVariable Long productId, @Valid @RequestBody UpsertProductSkuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(skuService.create(productId, request)));
    }

    /**
     * 更新商品规格。
     *
     * @param productId 商品主键
     * @param skuId 规格主键
     * @param request 规格请求
     * @return 更新后规格
     */
    @PutMapping("/admin/products/{productId}/skus/{skuId}")
    public ApiResponse<ProductSkuEntity> update(@PathVariable Long productId, @PathVariable Long skuId,
                                                 @Valid @RequestBody UpsertProductSkuRequest request) {
        return ApiResponse.success(skuService.update(productId, skuId, request));
    }

    /**
     * 删除商品规格。
     *
     * @param productId 商品主键
     * @param skuId 规格主键
     * @return 空响应
     */
    @DeleteMapping("/admin/products/{productId}/skus/{skuId}")
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long skuId) {
        skuService.delete(productId, skuId);
        return ApiResponse.success(null);
    }
}
