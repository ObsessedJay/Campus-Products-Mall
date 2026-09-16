package com.sk.onlinemall.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserCreditLog {
    private Long id;
    private Long userId;
    private Integer previousScore;
    private Integer adjustedScore;
    private Integer changeAmount;
    private String reason;
    private Long operatedBy;
    private LocalDateTime createdAt;
}
