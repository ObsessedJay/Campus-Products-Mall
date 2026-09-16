package com.sk.onlinemall.activity.model;

import java.time.LocalDateTime;

public record ReservationRosterItem(
        Long id,
        Long activityId,
        String activityName,
        Long userId,
        String email,
        String nickname,
        String reservationNo,
        ReservationStatus status,
        String lotteryBatchNo,
        Integer drawRank,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
