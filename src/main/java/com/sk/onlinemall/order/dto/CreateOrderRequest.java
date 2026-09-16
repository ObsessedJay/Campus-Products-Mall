package com.sk.onlinemall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String requestNo,
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        Long activityId,
        Long skuId) {

    /**
     * 创建不指定规格的兼容订单请求。
     *
     * @param requestNo 业务请求号
     * @param productId 商品主键
     * @param quantity 购买数量
     * @param activityId 活动主键
     */
    public CreateOrderRequest(String requestNo, Long productId, Integer quantity, Long activityId) {
        this(requestNo, productId, quantity, activityId, null);
    }
}
