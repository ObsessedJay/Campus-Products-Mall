package com.sk.onlinemall.review.model;

import java.time.LocalDateTime;

public record ReviewItem(
        ReviewContentType type,
        Long id,
        String name,
        String status,
        String reviewStatus,
        String submittedBy,
        LocalDateTime submittedAt) {
}
