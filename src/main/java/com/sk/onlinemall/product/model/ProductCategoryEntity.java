package com.sk.onlinemall.product.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductCategoryEntity {
    private Long id;
    private String name;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;

}
