package com.sk.onlinemall.pickup.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateOperatorPickupPointsRequest(
        @NotNull List<@NotNull Long> pickupPointIds) {
}
