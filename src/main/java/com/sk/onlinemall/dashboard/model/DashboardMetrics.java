package com.sk.onlinemall.dashboard.model;

import java.math.BigDecimal;

public record DashboardMetrics(
        long userCount,
        long productCount,
        long activityCount,
        long reservationCount,
        long orderCount,
        BigDecimal paidAmount,
        long pickupEligibleCount,
        long pickedUpCount,
        BigDecimal pickupRate) {
}
