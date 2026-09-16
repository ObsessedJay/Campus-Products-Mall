package com.sk.onlinemall.dashboard.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DashboardAggregate {
    private Long userCount;
    private Long productCount;
    private Long activityCount;
    private Long reservationCount;
    private Long orderCount;
    private BigDecimal paidAmount;
    private Long pickupEligibleCount;
    private Long pickedUpCount;
}
