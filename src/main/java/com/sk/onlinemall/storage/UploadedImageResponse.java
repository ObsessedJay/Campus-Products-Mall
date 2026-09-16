package com.sk.onlinemall.storage;

/**
 * 表示图片上传成功后的公开访问数据。
 *
 * @param objectName 服务端对象名称
 * @param url 同源访问地址
 * @param contentType 图片媒体类型
 * @param size 图片字节数
 */
public record UploadedImageResponse(String objectName, String url, String contentType, long size) {
}
