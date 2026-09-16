package com.sk.onlinemall.product.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductFavoriteEntity {
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createdAt;

}
