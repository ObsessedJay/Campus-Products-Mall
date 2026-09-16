package com.sk.onlinemall.notification.model;

import java.time.LocalDateTime;

public record UserMessageView(
        Long id,
        NotificationType type,
        String title,
        String content,
        String link,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
}
