package com.sk.onlinemall.order.model;

public enum OrderStatus {
    WAIT_PAYMENT,
    PAID,
    WAIT_VERIFICATION,
    COMPLETED,
    CANCELLED,
    REFUNDING,
    REFUNDED
}
