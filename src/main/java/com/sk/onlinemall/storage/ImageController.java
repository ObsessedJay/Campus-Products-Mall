package com.sk.onlinemall.storage;

import com.sk.onlinemall.common.api.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 提供图片上传、公开下载和运营删除接口。
 */
@RestController
@RequestMapping("/api/v1/files/images")
public class ImageController {
    private final ImageStorageService imageStorageService;

    /**
     * 创建图片文件控制器。
     *
     * @param imageStorageService 图片存储服务
     */
    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * 上传并校验一张图片。
     *
     * @param file 图片文件
     * @return 已保存图片信息
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadedImageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageStorageService.upload(file));
    }

    /**
     * 下载公开图片内容。
     *
     * @param objectName 对象名称
     * @return 图片二进制响应
     */
    @GetMapping("/{objectName}")
    public ResponseEntity<byte[]> download(@PathVariable String objectName) {
        StoredObject storedObject = imageStorageService.download(objectName);
        MediaType mediaType = MediaType.parseMediaType(storedObject.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(storedObject.content().length)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectName + "\"")
                .body(storedObject.content());
    }

    /**
     * 删除指定图片。
     *
     * @param objectName 对象名称
     * @return 空成功响应
     */
    @DeleteMapping("/{objectName}")
    public ApiResponse<Void> delete(@PathVariable String objectName) {
        imageStorageService.delete(objectName);
        return ApiResponse.success(null);
    }
}
