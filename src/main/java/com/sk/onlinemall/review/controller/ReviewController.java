package com.sk.onlinemall.review.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.review.dto.RejectReviewRequest;
import com.sk.onlinemall.review.model.ReviewItem;
import com.sk.onlinemall.review.model.ReviewLogEntity;
import com.sk.onlinemall.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    /**
     * 创建 ReviewController 实例。
     *
     * @param reviewService 审核业务服务
     */
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 待审核列表。
     *
     * @param type 审核内容类型
     * @param status 业务状态或连接关闭状态
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<List<ReviewItem>> pending(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "PENDING_REVIEW") String status) {
        return ApiResponse.success(reviewService.findPending(type, status));
    }

    /**
     * 通过审核。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{type}/{id}/approve")
    public ApiResponse<Object> approve(@PathVariable String type, @PathVariable Long id,
                                       Authentication authentication) {
        return ApiResponse.success(reviewService.approve(type, id, authentication.getName()));
    }

    /**
     * 驳回。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{type}/{id}/reject")
    public ApiResponse<Object> reject(@PathVariable String type, @PathVariable Long id,
                                      @Valid @RequestBody RejectReviewRequest request,
                                      Authentication authentication) {
        return ApiResponse.success(reviewService.reject(type, id, request.reason(), authentication.getName()));
    }

    /**
     * 审核日志。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @return 方法执行结果
     */
    @GetMapping("/{type}/{id}/logs")
    public ApiResponse<List<ReviewLogEntity>> logs(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.success(reviewService.findLogs(type, id));
    }

    /**
     * 全部审核日志。
     *
     * @param type 审核内容类型
     * @param contentId 审核内容主键
     * @param limit 最大返回数量
     * @return 方法执行结果
     */
    @GetMapping("/logs")
    public ApiResponse<List<ReviewLogEntity>> allLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long contentId,
            @RequestParam(defaultValue = "100") Integer limit) {
        return ApiResponse.success(reviewService.findLogs(type, contentId, limit));
    }
}
