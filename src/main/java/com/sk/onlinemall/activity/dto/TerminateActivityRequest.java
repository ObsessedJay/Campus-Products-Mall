package com.sk.onlinemall.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminateActivityRequest(
        @NotBlank @Size(max = 500) String reason) {
}
