package com.sk.onlinemall.system.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SystemConfigLog {
    private Long id;
    private String configKey;
    private String previousValue;
    private String updatedValue;
    private String reason;
    private Long operatedBy;
    private LocalDateTime createdAt;
}
