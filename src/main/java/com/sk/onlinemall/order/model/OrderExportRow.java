package com.sk.onlinemall.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderExportRow(
        Long orderId,
        String orderNo,
        Long activityId,
        Long userId,
        String nickname,
        String email,
        OrderStatus status,
        BigDecimal totalAmount,
        Long pickupPointId,
        String pickupPointName,
        String pickupPointAddress,
        String productName,
        String skuCode,
        String skuName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount,
        String pickupCode,
        String verifierNickname,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt) {
}
