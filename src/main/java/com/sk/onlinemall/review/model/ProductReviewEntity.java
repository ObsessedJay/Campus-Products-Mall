package com.sk.onlinemall.review.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductReviewEntity {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long userId;
    private Integer rating;
    private String content;
    private ProductReviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
