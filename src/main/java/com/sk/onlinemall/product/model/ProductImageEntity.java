package com.sk.onlinemall.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductImageEntity {
    private Long id;
    private Long productId;
    private String objectName;
    private String displayName;
    @JsonIgnore
    private String originalName;
    private String url;
    private String contentType;
    private Long sizeBytes;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
}
