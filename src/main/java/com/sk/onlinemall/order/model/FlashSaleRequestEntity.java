package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FlashSaleRequestEntity {
    private Long id;
    private String requestNo;
    private Long activityId;
    private Long productId;
    private Long skuId;
    private Long userId;
    private Integer quantity;
    private FlashSaleRequestStatus status;
    private Long orderId;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
