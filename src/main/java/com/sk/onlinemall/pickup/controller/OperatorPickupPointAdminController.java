package com.sk.onlinemall.pickup.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.pickup.dto.UpdateOperatorPickupPointsRequest;
import com.sk.onlinemall.pickup.model.PickupPointEntity;
import com.sk.onlinemall.pickup.service.OperatorPickupPointService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/operators/{operatorId}/pickup-points")
public class OperatorPickupPointAdminController {
    private final OperatorPickupPointService authorizationService;

    /**
     * 创建运营人员点位授权控制器。
     *
     * @param authorizationService 点位授权服务
     */
    public OperatorPickupPointAdminController(OperatorPickupPointService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * 查询运营人员的点位授权。
     *
     * @param operatorId 运营人员主键
     * @return 已授权自提点
     */
    @GetMapping
    public ApiResponse<List<PickupPointEntity>> find(@PathVariable Long operatorId) {
        return ApiResponse.success(authorizationService.findByOperator(operatorId));
    }

    /**
     * 替换运营人员的点位授权。
     *
     * @param operatorId 运营人员主键
     * @param request 授权更新请求
     * @return 更新后的授权自提点
     */
    @PutMapping
    public ApiResponse<List<PickupPointEntity>> replace(
            @PathVariable Long operatorId,
            @Valid @RequestBody UpdateOperatorPickupPointsRequest request) {
        return ApiResponse.success(authorizationService.replace(operatorId, request.pickupPointIds()));
    }
}
