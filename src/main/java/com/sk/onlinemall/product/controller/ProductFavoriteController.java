package com.sk.onlinemall.product.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.service.ProductFavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductFavoriteController {
    private final ProductFavoriteService favoriteService;

    /**
     * 创建 ProductFavoriteController 实例。
     *
     * @param favoriteService 收藏业务服务
     */
    public ProductFavoriteController(ProductFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 查询业务数据列表。
     *
     * @param authentication 当前登录身份
     * @return 查询结果
     */
    @GetMapping("/users/me/favorites")
    public ApiResponse<List<ProductEntity>> list(Authentication authentication) {
        return ApiResponse.success(favoriteService.findMine(authentication.getName()));
    }

    /**
     * 新增。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @PostMapping("/products/{id}/favorite")
    public ApiResponse<ProductEntity> add(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(favoriteService.add(id, authentication.getName()));
    }

    /**
     * 取消收藏指定商品。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @DeleteMapping("/products/{id}/favorite")
    public ApiResponse<Void> remove(@PathVariable Long id, Authentication authentication) {
        favoriteService.remove(id, authentication.getName());
        return ApiResponse.success(null);
    }
}
