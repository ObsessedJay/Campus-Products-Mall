package com.sk.onlinemall.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestRefundRequest(
        @NotBlank @Size(max = 500) String reason) {
}
