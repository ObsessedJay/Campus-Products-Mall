package com.sk.onlinemall.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayOrderRequest(
        @NotBlank @Size(max = 64) String idempotencyKey) {
}
