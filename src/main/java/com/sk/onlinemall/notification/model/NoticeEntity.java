package com.sk.onlinemall.notification.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeEntity {
    private Long id;
    private NotificationType noticeType;
    private String businessKey;
    private String title;
    private String content;
    private String link;
    private LocalDateTime createdAt;
}
