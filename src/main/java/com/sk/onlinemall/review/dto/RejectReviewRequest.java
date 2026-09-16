package com.sk.onlinemall.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReviewRequest(@NotBlank @Size(max = 500) String reason) {
}
