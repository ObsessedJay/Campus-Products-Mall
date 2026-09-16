package com.sk.onlinemall.product.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductEntity {
    private Long id;
    private Long categoryId;
    private Long createdBy;
    private Long pickupPointId;
    private String name;
    private String subtitle;
    private String description;
    private String coverUrl;
    private ProductSaleType saleType;
    private ProductStatus status;
    private BigDecimal price;
    private Integer stock;
    private Integer soldCount;
    private Integer limitPerUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
