package com.sk.onlinemall.product.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductSkuEntity {
    private Long id;
    private Long productId;
    private String skuCode;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
