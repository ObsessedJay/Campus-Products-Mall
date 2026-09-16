package com.sk.onlinemall.review.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.review.dto.CreateProductReviewRequest;
import com.sk.onlinemall.review.model.ProductReviewView;
import com.sk.onlinemall.review.service.ProductReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductReviewController {
    private final ProductReviewService reviewService;

    /**
     * 创建商品评价控制器。
     *
     * @param reviewService 商品评价服务
     */
    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 查询商品公开评价。
     *
     * @param productId 商品主键
     * @return 公开评价列表
     */
    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<List<ProductReviewView>> list(@PathVariable Long productId) {
        return ApiResponse.success(reviewService.findVisible(productId));
    }

    /**
     * 为已完成订单创建商品评价。
     *
     * @param orderId 订单主键
     * @param request 评价内容
     * @param authentication 当前登录身份
     * @return 新增评价
     */
    @PostMapping("/orders/{orderId}/reviews")
    public ApiResponse<ProductReviewView> create(@PathVariable Long orderId,
                                                  @Valid @RequestBody CreateProductReviewRequest request,
                                                  Authentication authentication) {
        return ApiResponse.success(reviewService.create(orderId, request, authentication.getName()));
    }
}
