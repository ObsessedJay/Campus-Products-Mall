package com.sk.onlinemall.user.model;

import java.util.List;

public record UserGovernanceDetail(
        UserEntity user,
        long reservationCount,
        long orderCount,
        long reportCount,
        List<UserCreditLog> creditLogs) {
}
