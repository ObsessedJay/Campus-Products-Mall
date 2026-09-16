package com.sk.onlinemall.storage;

import com.sk.onlinemall.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;

/**
 * 使用 MinIO 保存和读取图片对象。
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class MinioObjectStorage implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);
    private final MinioClient minioClient;
    private final String bucket;
    private volatile boolean bucketReady;

    /**
     * 创建 MinIO 对象存储适配器。
     *
     * @param endpoint MinIO 服务地址
     * @param accessKey MinIO 访问密钥
     * @param secretKey MinIO 私密密钥
     * @param bucket 存储桶名称
     */
    public MinioObjectStorage(
            @Value("${app.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey,
            @Value("${app.storage.bucket:campus-creative}") String bucket) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("MinIO credentials are required when file storage is enabled");
        }
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    /**
     * 将文件写入 MinIO。
     *
     * @param objectName 对象名称
     * @param content 文件内容
     * @param contentType 文件媒体类型
     */
    @Override
    public void put(String objectName, byte[] content, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * 从 MinIO 读取文件及其媒体类型。
     *
     * @param objectName 对象名称
     * @return 文件内容及媒体类型
     */
    @Override
    public StoredObject get(String objectName) {
        try {
            ensureBucket();
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build())) {
                return new StoredObject(response.readAllBytes(), stat.contentType());
            }
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())
                    || "NoSuchObject".equals(exception.errorResponse().code())) {
                throw new BusinessException("FILE_NOT_FOUND", "image does not exist");
            }
            throw storageFailure(exception);
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * 从 MinIO 删除文件。
     *
     * @param objectName 对象名称
     */
    @Override
    public void delete(String objectName) {
        try {
            ensureBucket();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * 首次访问时检查并创建存储桶。
     *
     * @throws Exception MinIO 请求失败时抛出
     */
    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }

    /**
     * 隐藏 MinIO 内部异常并转换为稳定业务错误。
     *
     * @param cause 原始异常
     * @return 文件存储异常
     */
    private BusinessException storageFailure(Exception cause) {
        log.error("MinIO operation failed for bucket {}", bucket, cause);
        return new BusinessException("STORAGE_ERROR", "file storage operation failed");
    }
}
