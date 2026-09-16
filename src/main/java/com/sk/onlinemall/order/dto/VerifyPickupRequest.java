package com.sk.onlinemall.order.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPickupRequest(@NotBlank String pickupCode) {
}
