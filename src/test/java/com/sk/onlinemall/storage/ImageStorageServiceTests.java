package com.sk.onlinemall.storage;

import com.sk.onlinemall.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImageStorageServiceTests {

    /**
     * 验证合法 PNG 图片按文件头识别并写入对象存储。
     */
    @Test
    void shouldUploadValidPngImage() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        ImageStorageService service = new ImageStorageService(objectStorage, 1024);
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);

        UploadedImageResponse response = service.upload(file);

        assertThat(response.objectName()).matches("[0-9a-f-]{36}\\.png");
        assertThat(response.url()).isEqualTo("/api/v1/files/images/" + response.objectName());
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.size()).isEqualTo(content.length);
        verify(objectStorage).put(eq(response.objectName()), eq(content), eq("image/png"));
    }

    /**
     * 验证伪造为图片的文本文件会被拒绝。
     */
    @Test
    void shouldRejectSpoofedImageContent() {
        ImageStorageService service = new ImageStorageService(mock(ObjectStorage.class), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_FILE_TYPE"));
    }

    /**
     * 验证声明类型与实际图片类型不一致时拒绝上传。
     */
    @Test
    void shouldRejectMismatchedContentType() {
        ImageStorageService service = new ImageStorageService(mock(ObjectStorage.class), 1024);
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_FILE_TYPE"));
    }

    /**
     * 验证超过大小上限的图片会在读取前被拒绝。
     */
    @Test
    void shouldRejectOversizedImage() {
        ImageStorageService service = new ImageStorageService(mock(ObjectStorage.class), 8);
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    /**
     * 验证读取接口拒绝路径形式的任意对象名。
     */
    @Test
    void shouldRejectUnsafeObjectName() {
        ImageStorageService service = new ImageStorageService(mock(ObjectStorage.class), 1024);

        assertThatThrownBy(() -> service.download("../secret.png"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_OBJECT_NAME"));
    }
}
