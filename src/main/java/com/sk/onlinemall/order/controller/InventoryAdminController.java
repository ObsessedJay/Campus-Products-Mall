package com.sk.onlinemall.order.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.order.dto.ReconcileInventoryRequest;
import com.sk.onlinemall.order.model.InventoryReconciliationEntity;
import com.sk.onlinemall.order.model.InventorySnapshot;
import com.sk.onlinemall.order.service.InventoryReconciliationService;
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
@RequestMapping("/api/v1/admin/activities/{activityId}/inventory")
public class InventoryAdminController {
    private final InventoryReconciliationService reconciliationService;

    /**
     * 创建 InventoryAdminController 实例。
     *
     * @param reconciliationService 库存校准业务服务
     */
    public InventoryAdminController(InventoryReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 库存快照。
     *
     * @param activityId 活动主键
     * @param authentication 当前登录身份
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<InventorySnapshot> snapshot(@PathVariable Long activityId,
                                                   Authentication authentication) {
        return ApiResponse.success(reconciliationService.snapshot(activityId, authentication.getName()));
    }

    /**
     * 查询活动库存校准记录。
     *
     * @param activityId 活动主键
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping("/reconciliations")
    public ApiResponse<List<InventoryReconciliationEntity>> reconciliations(
            @PathVariable Long activityId, Authentication authentication) {
        return ApiResponse.success(reconciliationService.recent(activityId, authentication.getName()));
    }

    /**
     * 校准库存。
     *
     * @param activityId 活动主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/reconcile")
    public ApiResponse<InventoryReconciliationEntity> reconcile(
            @PathVariable Long activityId, @Valid @RequestBody ReconcileInventoryRequest request,
            Authentication authentication) {
        return ApiResponse.success(reconciliationService.reconcile(
                activityId, request.reason(), authentication.getName()));
    }
}
