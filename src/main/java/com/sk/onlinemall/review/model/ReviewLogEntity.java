package com.sk.onlinemall.review.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewLogEntity {
    private Long id;
    private ReviewContentType contentType;
    private Long contentId;
    private Long reviewerId;
    private String reviewerUsername;
    private String result;
    private String reason;
    private LocalDateTime createdAt;

}
