package com.sk.onlinemall.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 64) String name,
        @Min(0) Integer sortOrder,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
}
