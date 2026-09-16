package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentRecordEntity {
    private Long id;
    private Long orderId;
    private String paymentNo;
    private String idempotencyKey;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

}
