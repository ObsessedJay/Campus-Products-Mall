package com.sk.onlinemall.storage;

/**
 * 定义业务层与具体对象存储实现之间的文件操作边界。
 */
public interface ObjectStorage {

    /**
     * 保存对象并覆盖同名内容。
     *
     * @param objectName 对象名称
     * @param content 文件内容
     * @param contentType 文件媒体类型
     */
    void put(String objectName, byte[] content, String contentType);

    /**
     * 读取指定对象。
     *
     * @param objectName 对象名称
     * @return 对象内容及媒体类型
     */
    StoredObject get(String objectName);

    /**
     * 删除指定对象。
     *
     * @param objectName 对象名称
     */
    void delete(String objectName);
}
