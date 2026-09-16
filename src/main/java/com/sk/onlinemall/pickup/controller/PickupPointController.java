package com.sk.onlinemall.pickup.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.pickup.model.PickupPointEntity;
import com.sk.onlinemall.pickup.service.PickupPointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pickup-points")
public class PickupPointController {
    private final PickupPointService pickupPointService;

    /**
     * 创建 PickupPointController 实例。
     *
     * @param pickupPointService 自提核销自提点业务服务
     */
    public PickupPointController(PickupPointService pickupPointService) {
        this.pickupPointService = pickupPointService;
    }

    /**
     * 查询列表启用的。
     *
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<List<PickupPointEntity>> listActive() {
        return ApiResponse.success(pickupPointService.findActive());
    }
}
