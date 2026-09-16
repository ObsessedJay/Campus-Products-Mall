package com.sk.onlinemall.activity.dto;

import com.sk.onlinemall.activity.model.ActivityMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateActivityRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull Long productId,
        @NotNull ActivityMode mode,
        LocalDateTime reservationStartAt,
        @Future LocalDateTime reservationEndAt,
        @NotNull @Future LocalDateTime startAt,
        @NotNull @Future LocalDateTime endAt,
        @NotNull @Min(1) Integer stock,
        @NotNull @Min(1) Integer limitPerUser,
        @NotNull @Min(1) Integer paymentTimeoutMinutes,
        @Size(max = 2000) String ruleDescription) {
}
