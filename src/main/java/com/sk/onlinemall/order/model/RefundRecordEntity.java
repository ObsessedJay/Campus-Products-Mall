package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RefundRecordEntity {
    private Long id;
    private Long orderId;
    private String refundNo;
    private String reason;
    private RefundStatus status;
    private Long processedBy;
    private LocalDateTime processedAt;
    private String processReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
