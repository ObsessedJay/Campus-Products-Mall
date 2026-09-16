package com.sk.onlinemall.user.dto;

public record AuthTokenResponse(String token, Long userId, String email, String nickname, String role) {
}
