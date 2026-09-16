package com.sk.onlinemall.storage;

/**
 * 表示从对象存储读取的文件。
 *
 * @param content 文件内容
 * @param contentType 文件媒体类型
 */
public record StoredObject(byte[] content, String contentType) {
}
