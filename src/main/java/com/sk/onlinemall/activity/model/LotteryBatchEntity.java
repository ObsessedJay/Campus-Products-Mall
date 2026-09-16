package com.sk.onlinemall.activity.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LotteryBatchEntity {
    private Long id;
    private Long activityId;
    private String batchNo;
    private Long randomSeed;
    private Integer totalReservations;
    private Integer winnerCount;
    private Long drawnBy;
    private LocalDateTime drawnAt;
    private LocalDateTime createdAt;

}
