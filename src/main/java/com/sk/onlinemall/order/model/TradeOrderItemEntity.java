package com.sk.onlinemall.order.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TradeOrderItemEntity {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String skuCode;
    private String skuName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;

}
