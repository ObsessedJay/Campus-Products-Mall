package com.sk.onlinemall.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MerchantProfileEntity {
    private Long accountId;
    private String merchantName;
    private String logoUrl;
    private String contactName;
    private String contactPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
