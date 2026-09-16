package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.product.dto.UpdateProductImageSortRequest;
import com.sk.onlinemall.product.model.ProductImageEntity;
import com.sk.onlinemall.product.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductImageController {
    private final ProductImageService productImageService;

    /**
     * 创建商品图片控制器。
     *
     * @param productImageService 商品图片服务
     */
    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    /**
     * 查询公开在售商品图片。
     *
     * @param productId 商品主键
     * @return 商品图片列表
     */
    @GetMapping("/products/{productId}/images")
    public ApiResponse<List<ProductImageEntity>> listPublic(@PathVariable Long productId) {
        return ApiResponse.success(productImageService.findPublic(productId));
    }

    /**
     * 查询运营侧商品图片。
     *
     * @param productId 商品主键
     * @return 商品图片列表
     */
    @GetMapping("/admin/products/{productId}/images")
    public ApiResponse<List<ProductImageEntity>> listForManagement(@PathVariable Long productId) {
        return ApiResponse.success(productImageService.findForManagement(productId));
    }

    /**
     * 上传图片并绑定到商品。
     *
     * @param productId 商品主键
     * @param file 图片文件
     * @param displayName 商家设置的图片展示名称
     * @param sortOrder 可选排序值
     * @param authentication 当前登录身份
     * @return 新增商品图片
     */
    @PostMapping(value = "/admin/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductImageEntity>> upload(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) Integer sortOrder,
            Authentication authentication) {
        ProductImageEntity image = productImageService.upload(
                productId, file, displayName, sortOrder, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(image));
    }

    /**
     * 调整商品图片排序。
     *
     * @param productId 商品主键
     * @param imageId 图片主键
     * @param request 排序请求
     * @return 更新后的商品图片
     */
    @PutMapping("/admin/products/{productId}/images/{imageId}/sort")
    public ApiResponse<ProductImageEntity> updateSortOrder(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateProductImageSortRequest request) {
        return ApiResponse.success(productImageService.updateSortOrder(productId, imageId, request.sortOrder()));
    }

    /**
     * 删除商品图片。
     *
     * @param productId 商品主键
     * @param imageId 图片主键
     * @return 空成功响应
     */
    @DeleteMapping("/admin/products/{productId}/images/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ApiResponse.success(null);
    }
}
