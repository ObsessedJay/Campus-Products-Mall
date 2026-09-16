package com.sk.onlinemall.order.controller;

import com.sk.onlinemall.order.service.OrderExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class OrderManagementController {
    private final OrderExportService orderExportService;

    /**
     * 创建运营订单控制器。
     *
     * @param orderExportService 订单导出服务
     */
    public OrderManagementController(OrderExportService orderExportService) {
        this.orderExportService = orderExportService;
    }

    /**
     * 按筛选条件导出订单及核销结果。
     *
     * @param activityId 可选活动主键
     * @param status 可选订单状态
     * @param pickupPointId 可选自提点主键
     * @param verificationStatus 可选核销状态
     * @param authentication 当前登录身份
     * @return XLSX 文件响应
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long pickupPointId,
            @RequestParam(required = false) String verificationStatus,
            Authentication authentication) {
        byte[] content = orderExportService.export(
                activityId, status, pickupPointId, verificationStatus, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("orders-and-verifications.xlsx").build().toString())
                .contentLength(content.length)
                .body(content);
    }
}
