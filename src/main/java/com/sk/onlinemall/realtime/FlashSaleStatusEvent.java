package com.sk.onlinemall.realtime;

import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestStatus;

import java.time.LocalDateTime;

public record FlashSaleStatusEvent(
        String type,
        String requestNo,
        FlashSaleRequestStatus status,
        Long orderId,
        String failureCode,
        String failureMessage,
        LocalDateTime updatedAt) {

    /**
     * 创建等待处理的抢购状态事件。
     *
     * @return 方法执行结果
     */
    public static FlashSaleStatusEvent ready() {
        return new FlashSaleStatusEvent("READY", null, null, null, null, null, LocalDateTime.now());
    }

    /**
     * 来源于。
     *
     * @param request 请求参数
     * @return 方法执行结果
     */
    public static FlashSaleStatusEvent from(FlashSaleRequestEntity request) {
        return new FlashSaleStatusEvent("FLASH_SALE_STATUS", request.getRequestNo(), request.getStatus(),
                request.getOrderId(), request.getFailureCode(), request.getFailureMessage(),
                request.getUpdatedAt() == null ? LocalDateTime.now() : request.getUpdatedAt());
    }
}
