package com.sk.onlinemall.realtime;

import com.sk.onlinemall.order.model.TradeOrderEntity;

import java.time.LocalDateTime;

public record OrderStatusEvent(String type, TradeOrderEntity order, LocalDateTime updatedAt) {
    /**
     * 来源于。
     *
     * @param order 订单信息
     * @return 方法执行结果
     */
    public static OrderStatusEvent from(TradeOrderEntity order) {
        return new OrderStatusEvent("ORDER_STATUS", order, LocalDateTime.now());
    }
}
