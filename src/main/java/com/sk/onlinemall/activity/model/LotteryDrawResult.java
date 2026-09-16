package com.sk.onlinemall.activity.model;

import java.util.List;

public record LotteryDrawResult(
        LotteryBatchEntity batch,
        List<ActivityReservationEntity> reservations) {
}
