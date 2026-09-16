package com.sk.onlinemall.order.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.order.dto.RetryFlashSaleRequest;
import com.sk.onlinemall.order.model.FlashSaleCompensationEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.service.FlashSaleRequestService;
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
@RequestMapping("/api/v1/admin/flash-sale/failed-requests")
public class FlashSaleCompensationController {
    private final FlashSaleRequestService requestService;

    /**
     * 创建 FlashSaleCompensationController 实例。
     *
     * @param requestService 抢购请求业务服务
     */
    public FlashSaleCompensationController(FlashSaleRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * 查询失败的抢购请求。
     *
     * @param activityId 活动主键
     * @param failureCode 失败原因编码
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping
    public ApiResponse<List<FlashSaleRequestEntity>> failedRequests(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String failureCode,
            Authentication authentication) {
        return ApiResponse.success(requestService.findFailed(activityId, failureCode, authentication.getName()));
    }

    /**
     * 补偿记录。
     *
     * @param requestNo 业务请求号
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping("/{requestNo}/compensations")
    public ApiResponse<List<FlashSaleCompensationEntity>> compensations(
            @PathVariable String requestNo, Authentication authentication) {
        return ApiResponse.success(requestService.findCompensations(requestNo, authentication.getName()));
    }

    /**
     * 重试。
     *
     * @param requestNo 业务请求号
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{requestNo}/retry")
    public ApiResponse<FlashSaleCompensationEntity> retry(
            @PathVariable String requestNo, @Valid @RequestBody RetryFlashSaleRequest request,
            Authentication authentication) {
        return ApiResponse.success(requestService.compensate(
                requestNo, request.reason(), authentication.getName()));
    }
}
