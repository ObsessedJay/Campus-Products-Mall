package com.sk.onlinemall.product.mapper;

import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductFavoriteEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductFavoriteMapper {
    /**
     * 查询用户对指定商品的收藏记录。
     *
     * @param userId 用户主键
     * @param productId 商品主键
     * @return 查询结果
     */
    @Select("SELECT id, user_id, product_id, created_at FROM user_favorite WHERE user_id = #{userId} AND product_id = #{productId}")
    ProductFavoriteEntity find(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 新增商品收藏记录。
     *
     * @param favorite 商品收藏记录
     * @return 数据库受影响行数
     */
    @Insert("INSERT INTO user_favorite (user_id, product_id) VALUES (#{userId}, #{productId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductFavoriteEntity favorite);

    /**
     * 删除指定记录。
     *
     * @param userId 用户主键
     * @param productId 商品主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM user_favorite WHERE user_id = #{userId} AND product_id = #{productId}")
    int delete(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 查询用户收藏的商品。
     *
     * @param userId 用户主键
     * @return 查询结果
     */
    @Select("""
            SELECT p.id, p.category_id, p.name, p.subtitle, p.description, p.cover_url, p.sale_type,
                   p.status, p.price, p.stock, p.sold_count, p.limit_per_user, p.created_at, p.updated_at
            FROM user_favorite f JOIN product p ON p.id = f.product_id
            WHERE f.user_id = #{userId}
            ORDER BY f.created_at DESC, f.id DESC
            """)
    List<ProductEntity> findProducts(@Param("userId") Long userId);
}
