package com.sk.onlinemall.storage;

import com.sk.onlinemall.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

/**
 * 在未配置 MinIO 时提供明确失败的对象存储实现。
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledObjectStorage implements ObjectStorage {

    /**
     * 拒绝在对象存储未启用时写入文件。
     *
     * @param objectName 对象名称
     * @param content 文件内容
     * @param contentType 文件媒体类型
     */
    @Override
    public void put(String objectName, byte[] content, String contentType) {
        throw unavailable();
    }

    /**
     * 拒绝在对象存储未启用时读取文件。
     *
     * @param objectName 对象名称
     * @return 不会返回
     */
    @Override
    public StoredObject get(String objectName) {
        throw unavailable();
    }

    /**
     * 拒绝在对象存储未启用时删除文件。
     *
     * @param objectName 对象名称
     */
    @Override
    public void delete(String objectName) {
        throw unavailable();
    }

    /**
     * 创建统一的存储未启用异常。
     *
     * @return 存储未启用异常
     */
    private BusinessException unavailable() {
        return new BusinessException("STORAGE_UNAVAILABLE", "file storage is not enabled");
    }
}
