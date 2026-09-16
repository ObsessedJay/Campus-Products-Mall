package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TradeOrderEntity {
    private Long id;
    private String orderNo;
    private String requestNo;
    private Long userId;
    private Long activityId;
    private Long pickupPointId;
    private String pickupPointName;
    private String pickupPointAddress;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime paymentDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
