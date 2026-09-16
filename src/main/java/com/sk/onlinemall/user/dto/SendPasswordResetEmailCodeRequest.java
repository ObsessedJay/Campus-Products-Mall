package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendPasswordResetEmailCodeRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank String captchaId,
        @NotBlank String captchaCode,
        @NotBlank @Pattern(regexp = "STUDENT|MERCHANT|ADMIN") String portalRole
) {
}
