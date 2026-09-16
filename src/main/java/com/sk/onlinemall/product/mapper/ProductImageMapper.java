package com.sk.onlinemall.product.mapper;

import com.sk.onlinemall.product.model.ProductImageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductImageMapper {

    /**
     * 查询商品图片并按运营顺序排列。
     *
     * @param productId 商品主键
     * @return 商品图片列表
     */
    @Select("""
            SELECT id, product_id, object_name, display_name, original_name, url, content_type, size_bytes,
                   sort_order, created_by, created_at
            FROM product_image
            WHERE product_id = #{productId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductImageEntity> findByProductId(Long productId);

    /**
     * 查询商品下的指定图片。
     *
     * @param id 图片主键
     * @param productId 商品主键
     * @return 商品图片，不存在时返回空
     */
    @Select("""
            SELECT id, product_id, object_name, display_name, original_name, url, content_type, size_bytes,
                   sort_order, created_by, created_at
            FROM product_image
            WHERE id = #{id} AND product_id = #{productId}
            """)
    ProductImageEntity findByIdAndProductId(@Param("id") Long id, @Param("productId") Long productId);

    /**
     * 统计商品图片数量。
     *
     * @param productId 商品主键
     * @return 图片数量
     */
    @Select("SELECT COUNT(*) FROM product_image WHERE product_id = #{productId}")
    int countByProductId(Long productId);

    /**
     * 计算商品下一张图片的默认排序值。
     *
     * @param productId 商品主键
     * @return 默认排序值
     */
    @Select("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM product_image WHERE product_id = #{productId}")
    int nextSortOrder(Long productId);

    /**
     * 新增商品图片记录。
     *
     * @param image 商品图片
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO product_image
              (product_id, object_name, display_name, original_name, url, content_type, size_bytes, sort_order, created_by)
            VALUES
              (#{productId}, #{objectName}, #{displayName}, #{originalName}, #{url}, #{contentType},
               #{sizeBytes}, #{sortOrder}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductImageEntity image);

    /**
     * 更新商品图片排序值。
     *
     * @param id 图片主键
     * @param productId 商品主键
     * @param sortOrder 排序值
     * @return 数据库受影响行数
     */
    @Update("UPDATE product_image SET sort_order = #{sortOrder} WHERE id = #{id} AND product_id = #{productId}")
    int updateSortOrder(@Param("id") Long id, @Param("productId") Long productId,
                        @Param("sortOrder") int sortOrder);

    /**
     * 删除商品图片记录。
     *
     * @param id 图片主键
     * @param productId 商品主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM product_image WHERE id = #{id} AND product_id = #{productId}")
    int delete(@Param("id") Long id, @Param("productId") Long productId);
}
