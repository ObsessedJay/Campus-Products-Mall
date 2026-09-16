package com.sk.onlinemall.product.mapper;

import com.sk.onlinemall.product.model.ProductCategoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductCategoryMapper {

    /**
     * 查询启用的。
     *
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, sort_order, status, created_at
            FROM product_category
            WHERE status = 'ACTIVE'
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCategoryEntity> findActive();

    /**
     * 查询全部商品分类供运营管理。
     *
     * @return 全部分类
     */
    @Select("""
            SELECT id, name, sort_order, status, created_at
            FROM product_category
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCategoryEntity> findAll();

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("SELECT id, name, sort_order, status, created_at FROM product_category WHERE id = #{id}")
    ProductCategoryEntity findById(Long id);

    /**
     * 按名称查询记录。
     *
     * @param name 分类名称
     * @return 查询结果
     */
    @Select("SELECT id, name, sort_order, status, created_at FROM product_category WHERE name = #{name}")
    ProductCategoryEntity findByName(String name);

    /**
     * 新增商品分类。
     *
     * @param category 商品分类
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO product_category (name, sort_order, status)
            VALUES (#{name}, #{sortOrder}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductCategoryEntity category);

    /**
     * 更新商品分类资料和状态。
     *
     * @param category 商品分类
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE product_category
            SET name = #{name}, sort_order = #{sortOrder}, status = #{status}
            WHERE id = #{id}
            """)
    int update(ProductCategoryEntity category);

    /**
     * 统计分类下的商品数量。
     *
     * @param categoryId 分类主键
     * @return 商品数量
     */
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId}")
    int countProducts(Long categoryId);

    /**
     * 删除没有商品引用的分类。
     *
     * @param categoryId 分类主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM product_category WHERE id = #{categoryId}")
    int delete(Long categoryId);
}
