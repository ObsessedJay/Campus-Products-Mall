package com.sk.onlinemall.review.model;

import java.time.LocalDateTime;

public record ProductReviewRow(
        Long id,
        Long productId,
        String nickname,
        Integer rating,
        String content,
        LocalDateTime createdAt) {
}
