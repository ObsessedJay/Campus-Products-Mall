package com.sk.onlinemall.activity.dto;

import jakarta.validation.constraints.Min;

public record RunLotteryRequest(
        Long seed,
        @Min(1) Integer winnerCount) {
}
