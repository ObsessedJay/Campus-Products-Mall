package com.sk.onlinemall.order.model;

import java.time.LocalDateTime;

public record InventorySnapshot(
        Long activityId,
        String activityName,
        Integer databaseAvailableStock,
        Integer redisAvailableStock,
        Integer pendingQuantity,
        Integer paidQuantity,
        Integer expectedRedisStock,
        boolean redisAvailable,
        boolean consistent,
        LocalDateTime checkedAt) {
}
