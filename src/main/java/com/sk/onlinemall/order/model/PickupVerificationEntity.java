package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PickupVerificationEntity {
    private Long id;
    private Long orderId;
    private String pickupCode;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;

}
