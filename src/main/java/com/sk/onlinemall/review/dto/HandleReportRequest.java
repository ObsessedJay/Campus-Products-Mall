package com.sk.onlinemall.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HandleReportRequest(@NotBlank @Size(max = 500) String result) {
}
