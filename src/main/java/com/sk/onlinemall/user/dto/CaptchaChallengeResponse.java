package com.sk.onlinemall.user.dto;

public record CaptchaChallengeResponse(
        String captchaId,
        String imageData,
        long expiresInSeconds
) {
}
