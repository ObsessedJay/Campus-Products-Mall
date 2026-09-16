package com.sk.onlinemall.dashboard.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.dashboard.model.DashboardMetrics;
import com.sk.onlinemall.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    /**
     * 创建数据看板控制器。
     *
     * @param dashboardService 数据看板服务
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 查询运营聚合指标。
     *
     * @param startDate 可选开始日期
     * @param endDate 可选结束日期
     * @param activityId 可选活动主键
     * @param productId 可选商品主键
     * @return 看板指标
     */
    @GetMapping
    public ApiResponse<DashboardMetrics> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Long productId) {
        return ApiResponse.success(dashboardService.metrics(startDate, endDate, activityId, productId));
    }
}
