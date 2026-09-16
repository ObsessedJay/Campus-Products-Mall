package com.sk.onlinemall.user.model;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String school,
        String role
) {
}
