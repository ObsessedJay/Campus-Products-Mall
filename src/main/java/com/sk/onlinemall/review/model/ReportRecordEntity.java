package com.sk.onlinemall.review.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportRecordEntity {
    private Long id;
    private Long reporterId;
    private String reporterNickname;
    private ReportTargetType targetType;
    private Long targetId;
    private String targetSummary;
    private String reason;
    private ReportStatus status;
    private Long handledBy;
    private String handlerNickname;
    private String handleResult;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
