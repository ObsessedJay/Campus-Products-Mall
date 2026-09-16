package com.sk.onlinemall.storage;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StorageObjectMapper {
    /**
     * 登记已上传对象。
     *
     * @param objectName 对象名
     * @param contentType 媒体类型
     * @param sizeBytes 字节数
     * @return 受影响行数
     */
    @Insert("INSERT INTO storage_object (object_name, content_type, size_bytes) VALUES (#{objectName}, #{contentType}, #{sizeBytes})")
    int insert(@Param("objectName") String objectName, @Param("contentType") String contentType,
               @Param("sizeBytes") long sizeBytes);

    /**
     * 删除对象台账。
     *
     * @param objectName 对象名
     * @return 受影响行数
     */
    @Delete("DELETE FROM storage_object WHERE object_name = #{objectName}")
    int delete(String objectName);

    /**
     * 查询超过保留期且未被引用的对象。
     *
     * @param cutoff 截止时间
     * @return 孤儿对象名
     */
    @Select("""
            SELECT s.object_name FROM storage_object s
            WHERE s.created_at < #{cutoff}
              AND NOT EXISTS (SELECT 1 FROM product_image p WHERE p.object_name = s.object_name)
              AND NOT EXISTS (SELECT 1 FROM product_review_image r
                              WHERE r.image_url = CONCAT('/api/v1/files/images/', s.object_name))
              AND NOT EXISTS (SELECT 1 FROM merchant_profile m
                              WHERE m.logo_url = CONCAT('/api/v1/files/images/', s.object_name))
            ORDER BY s.created_at LIMIT 200
            """)
    List<String> findOrphans(LocalDateTime cutoff);

    /**
     * 判断对象是否被业务记录引用。
     *
     * @param objectName 对象名
     * @return 引用数量
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM product_image WHERE object_name = #{objectName})
                 + (SELECT COUNT(*) FROM product_review_image
                    WHERE image_url = CONCAT('/api/v1/files/images/', #{objectName}))
                 + (SELECT COUNT(*) FROM merchant_profile
                    WHERE logo_url = CONCAT('/api/v1/files/images/', #{objectName}))
            """)
    long countReferences(String objectName);
}
