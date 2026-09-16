package com.sk.onlinemall.user.model;

import com.sk.onlinemall.user.dto.AuthTokenResponse;

public record AuthSession(AuthTokenResponse response, String refreshToken, long refreshTokenTtlSeconds) {
}
