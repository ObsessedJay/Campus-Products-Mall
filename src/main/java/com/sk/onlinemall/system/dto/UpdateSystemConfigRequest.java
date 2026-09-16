package com.sk.onlinemall.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemConfigRequest(
        @NotBlank @Size(max = 255) String value,
        @NotBlank @Size(max = 500) String reason) {
}
