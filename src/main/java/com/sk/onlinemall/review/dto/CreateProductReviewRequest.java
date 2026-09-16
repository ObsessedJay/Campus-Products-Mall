package com.sk.onlinemall.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProductReviewRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 3) List<@NotBlank @Size(max = 500) String> imageUrls) {
}
