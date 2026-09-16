package com.sk.onlinemall.review.model;

import java.time.LocalDateTime;
import java.util.List;

public record ProductReviewView(
        Long id,
        Long productId,
        String nickname,
        Integer rating,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt) {
}
