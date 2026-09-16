package com.sk.onlinemall.user.dto;

public record EmailCodeResponse(long expiresInSeconds, long retryAfterSeconds) {
}
