package com.sk.onlinemall.notification.model;

public record NotificationMessage(
        Long userId,
        NotificationType type,
        String businessKey,
        String title,
        String content,
        String link) {
}
