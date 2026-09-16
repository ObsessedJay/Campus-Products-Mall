package com.sk.onlinemall.storage;

import com.sk.onlinemall.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 负责图片安全校验、对象命名和持久化调用。
 */
@Service
public class ImageStorageService {
    private static final Pattern OBJECT_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|gif|webp)$");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    private final ObjectStorage objectStorage;
    private final long maxBytes;
    private final ImageCompressor imageCompressor;
    private final StorageObjectMapper storageObjectMapper;

    /**
     * 创建图片存储服务。
     *
     * @param objectStorage 对象存储适配器
     * @param maxBytes 单张图片最大字节数
     * @param imageCompressor 图片压缩组件
     * @param storageObjectMapper 对象台账组件
     */
    @Autowired
    public ImageStorageService(
            ObjectStorage objectStorage,
            @Value("${app.storage.max-image-bytes:5242880}") long maxBytes,
            ImageCompressor imageCompressor,
            StorageObjectMapper storageObjectMapper) {
        this.objectStorage = objectStorage;
        this.maxBytes = maxBytes;
        this.imageCompressor = imageCompressor;
        this.storageObjectMapper = storageObjectMapper;
    }

    /**
     * 创建用于独立单元测试的图片存储服务。
     *
     * @param objectStorage 对象存储适配器
     * @param maxBytes 单张图片最大字节数
     */
    ImageStorageService(ObjectStorage objectStorage, long maxBytes) {
        this.objectStorage = objectStorage;
        this.maxBytes = maxBytes;
        this.imageCompressor = new ImageCompressor(1920, 0.82F);
        this.storageObjectMapper = null;
    }

    /**
     * 校验图片内容并保存为随机对象名。
     *
     * @param file 上传图片
     * @return 已保存图片信息
     */
    public UploadedImageResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "image file cannot be empty");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "image file exceeds the configured size limit");
        }
        byte[] content = readContent(file);
        String detectedType = detectContentType(content);
        String declaredType = normalizeContentType(file.getContentType());
        if (detectedType == null || declaredType != null && !declaredType.equals(detectedType)) {
            throw new BusinessException("INVALID_FILE_TYPE", "only PNG, JPEG, GIF and WebP images are allowed");
        }
        String objectName = UUID.randomUUID() + "." + EXTENSIONS.get(detectedType);
        content = imageCompressor.compress(content, detectedType);
        objectStorage.put(objectName, content, detectedType);
        try {
            if (storageObjectMapper != null) {
                storageObjectMapper.insert(objectName, detectedType, content.length);
            }
        } catch (RuntimeException exception) {
            objectStorage.delete(objectName);
            throw exception;
        }
        return new UploadedImageResponse(
                objectName,
                "/api/v1/files/images/" + objectName,
                detectedType,
                content.length);
    }

    /**
     * 读取已保存图片。
     *
     * @param objectName 对象名称
     * @return 图片内容及媒体类型
     */
    public StoredObject download(String objectName) {
        validateObjectName(objectName);
        return objectStorage.get(objectName);
    }

    /**
     * 删除已保存图片。
     *
     * @param objectName 对象名称
     */
    public void delete(String objectName) {
        validateObjectName(objectName);
        if (storageObjectMapper != null && storageObjectMapper.countReferences(objectName) > 0) {
            throw new BusinessException("FILE_IN_USE", "image is referenced by business data");
        }
        objectStorage.delete(objectName);
        if (storageObjectMapper != null) {
            storageObjectMapper.delete(objectName);
        }
    }

    /**
     * 安全读取上传内容并转换读取异常。
     *
     * @param file 上传图片
     * @return 文件字节
     */
    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("FILE_READ_ERROR", "image file could not be read");
        }
    }

    /**
     * 根据文件头识别受支持的图片格式。
     *
     * @param content 文件字节
     * @return 识别出的媒体类型，无法识别时返回空
     */
    private String detectContentType(byte[] content) {
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(content, 'G', 'I', 'F', '8', '7', 'a')
                || startsWith(content, 'G', 'I', 'F', '8', '9', 'a')) {
            return "image/gif";
        }
        if (startsWith(content, 'R', 'I', 'F', 'F')
                && startsWithAt(content, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    /**
     * 判断文件是否以指定字节序列开头。
     *
     * @param content 文件字节
     * @param expected 期望的无符号字节序列
     * @return 是否匹配
     */
    private boolean startsWith(byte[] content, int... expected) {
        return startsWithAt(content, 0, expected);
    }

    /**
     * 判断文件指定偏移处是否匹配字节序列。
     *
     * @param content 文件字节
     * @param offset 起始偏移
     * @param expected 期望的无符号字节序列
     * @return 是否匹配
     */
    private boolean startsWithAt(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((content[offset + index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 规范化浏览器声明的媒体类型。
     *
     * @param contentType 声明的媒体类型
     * @return 规范化值，未声明时返回空
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 限制对象名为本服务生成的图片名称。
     *
     * @param objectName 对象名称
     */
    private void validateObjectName(String objectName) {
        if (objectName == null || !OBJECT_NAME.matcher(objectName).matches()) {
            throw new BusinessException("INVALID_OBJECT_NAME", "image object name is invalid");
        }
    }
}
