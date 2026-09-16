package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InventoryReconciliationEntity {
    private Long id;
    private Long activityId;
    private Integer databaseStock;
    private Integer redisStockBefore;
    private Integer redisStockAfter;
    private Integer pendingQuantity;
    private Integer differenceBefore;
    private String reason;
    private Long adjustedBy;
    private String status;
    private String failureMessage;
    private LocalDateTime createdAt;

}
