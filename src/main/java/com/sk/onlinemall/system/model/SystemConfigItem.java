package com.sk.onlinemall.system.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SystemConfigItem {
    private String configKey;
    private String configValue;
    private String valueType;
    private String description;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
