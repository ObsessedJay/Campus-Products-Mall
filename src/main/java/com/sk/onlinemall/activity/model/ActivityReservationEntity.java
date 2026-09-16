package com.sk.onlinemall.activity.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ActivityReservationEntity {
    private Long id;
    private Long activityId;
    private Long userId;
    private String reservationNo;
    private ReservationStatus status;
    private Long lotteryBatchId;
    private Integer drawRank;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
