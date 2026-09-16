package com.sk.onlinemall.activity.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FlashActivityEntity {
    private Long id;
    private String name;
    private Long productId;
    private Long pickupPointId;
    private Long createdBy;
    private ActivityMode mode;
    private ActivityStatus status;
    /** Review state is kept separately so legacy UNPUBLISHED activities remain readable. */
    private String reviewStatus;
    private LocalDateTime reservationStartAt;
    private LocalDateTime reservationEndAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer stock;
    private Integer limitPerUser;
    private Integer paymentTimeoutMinutes;
    private String ruleDescription;
    private String terminateReason;
    private LocalDateTime terminatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
