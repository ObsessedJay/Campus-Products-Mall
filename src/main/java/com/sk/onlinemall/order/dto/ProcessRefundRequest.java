package com.sk.onlinemall.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessRefundRequest(
        @NotBlank @Size(max = 500) String reason) {
}
