package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailCodeRequest(
        @NotBlank @Email String email,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}
