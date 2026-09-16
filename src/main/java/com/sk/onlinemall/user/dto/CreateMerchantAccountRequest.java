package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMerchantAccountRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and numbers")
        String password,
        @NotBlank @Size(min = 8, max = 72) String confirmPassword,
        @NotBlank @Size(min = 2, max = 64) String displayName,
        @NotBlank @Size(min = 2, max = 128) String merchantName,
        @Size(max = 64) String contactName,
        @Size(max = 32) String contactPhone) {
}
