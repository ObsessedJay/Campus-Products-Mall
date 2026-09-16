package com.sk.onlinemall.pickup.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PickupPointEntity {
    private Long id;
    private String name;
    private String campus;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String openingHours;
    private String contactPhone;
    private PickupPointStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
