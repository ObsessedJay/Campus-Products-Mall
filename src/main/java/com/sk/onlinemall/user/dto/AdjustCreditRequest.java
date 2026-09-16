package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustCreditRequest(
        @NotNull @Min(0) @Max(100) Integer score,
        @NotBlank @Size(max = 500) String reason) {
}
