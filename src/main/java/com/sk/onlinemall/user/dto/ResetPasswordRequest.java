package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String emailCode,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and numbers")
        String newPassword,
        @NotBlank @Pattern(regexp = "STUDENT|MERCHANT|ADMIN") String portalRole) {
}
