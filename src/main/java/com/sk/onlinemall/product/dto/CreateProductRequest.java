package com.sk.onlinemall.product.dto;

import com.sk.onlinemall.product.model.ProductSaleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        Long categoryId,
        @NotNull Long pickupPointId,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 255) String subtitle,
        @Size(max = 2000) String description,
        @Size(max = 500) String coverUrl,
        @NotNull ProductSaleType saleType,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull @Min(0) Integer stock,
        @Min(1) Integer limitPerUser) {
}
