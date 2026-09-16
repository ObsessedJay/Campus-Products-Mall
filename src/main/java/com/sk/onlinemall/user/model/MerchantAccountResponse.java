package com.sk.onlinemall.user.model;

import java.time.LocalDateTime;

public record MerchantAccountResponse(
        Long accountId,
        String email,
        String displayName,
        String role,
        String status,
        String merchantName,
        String contactName,
        String contactPhone,
        LocalDateTime createdAt) {
}
