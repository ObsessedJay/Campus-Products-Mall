package com.sk.onlinemall.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrphanImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(OrphanImageCleanupService.class);
    private final StorageObjectMapper objectMapper;
    private final ObjectStorage objectStorage;
    private final int retentionHours;

    /**
     * 创建孤儿图片清理服务。
     *
     * @param objectMapper 对象台账组件
     * @param objectStorage 对象存储组件
     * @param retentionHours 孤儿保留小时数
     */
    public OrphanImageCleanupService(StorageObjectMapper objectMapper, ObjectStorage objectStorage,
                                     @Value("${app.storage.orphan-retention-hours:24}") int retentionHours) {
        this.objectMapper = objectMapper;
        this.objectStorage = objectStorage;
        this.retentionHours = retentionHours;
    }

    /**
     * 定时删除超过保留期且仍无引用的图片。
     */
    @Scheduled(fixedDelayString = "${app.storage.cleanup-scan-ms:3600000}")
    public void cleanup() {
        for (String objectName : objectMapper.findOrphans(LocalDateTime.now().minusHours(retentionHours))) {
            try {
                if (objectMapper.countReferences(objectName) == 0) {
                    objectStorage.delete(objectName);
                    objectMapper.delete(objectName);
                    log.info("Deleted orphan image object {}", objectName);
                }
            } catch (RuntimeException exception) {
                log.error("Failed to clean orphan image object {}", objectName, exception);
            }
        }
    }
}
