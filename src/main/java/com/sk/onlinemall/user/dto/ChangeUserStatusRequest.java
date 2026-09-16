package com.sk.onlinemall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUserStatusRequest(@NotBlank @Size(max = 500) String reason) {
}
