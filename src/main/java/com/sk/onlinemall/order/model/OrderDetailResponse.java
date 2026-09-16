package com.sk.onlinemall.order.model;

import java.util.List;

public record OrderDetailResponse(
        TradeOrderEntity order,
        List<TradeOrderItemEntity> items,
        PaymentRecordEntity payment,
        PickupVerificationEntity pickupVerification,
        RefundRecordEntity refund) {
}
