package com.sk.onlinemall.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpsertProductSkuRequest(
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 128) String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotNull @Min(0) Integer stock,
        @NotNull Boolean enabled,
        @NotNull @Min(0) Integer sortOrder) {
}
