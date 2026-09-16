package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FlashSaleCompensationEntity {
    private Long id;
    private Long requestId;
    private String requestNo;
    private Long operatedBy;
    private String reason;
    private String previousFailureCode;
    private String previousFailureMessage;
    private String status;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

}
