package com.sk.onlinemall.product.mapper;

import com.sk.onlinemall.product.model.ProductSkuEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductSkuMapper {

    /**
     * 查询商品的全部规格。
     *
     * @param productId 商品主键
     * @return 规格列表
     */
    @Select("""
            SELECT id, product_id, sku_code, name, price, stock, enabled, sort_order, created_at, updated_at
            FROM product_sku WHERE product_id = #{productId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductSkuEntity> findByProductId(Long productId);

    /**
     * 查询商品的启用规格。
     *
     * @param productId 商品主键
     * @return 启用规格列表
     */
    @Select("""
            SELECT id, product_id, sku_code, name, price, stock, enabled, sort_order, created_at, updated_at
            FROM product_sku WHERE product_id = #{productId} AND enabled = TRUE
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductSkuEntity> findEnabledByProductId(Long productId);

    /**
     * 按主键查询规格。
     *
     * @param id 规格主键
     * @return 规格信息
     */
    @Select("""
            SELECT id, product_id, sku_code, name, price, stock, enabled, sort_order, created_at, updated_at
            FROM product_sku WHERE id = #{id}
            """)
    ProductSkuEntity findById(Long id);

    /**
     * 统计商品规格数量。
     *
     * @param productId 商品主键
     * @return 规格数量
     */
    @Select("SELECT COUNT(*) FROM product_sku WHERE product_id = #{productId}")
    int countByProductId(Long productId);

    /**
     * 新增商品规格。
     *
     * @param sku 规格信息
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO product_sku (product_id, sku_code, name, price, stock, enabled, sort_order)
            VALUES (#{productId}, #{skuCode}, #{name}, #{price}, #{stock}, #{enabled}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductSkuEntity sku);

    /**
     * 更新商品规格。
     *
     * @param sku 规格信息
     * @return 受影响行数
     */
    @Update("""
            UPDATE product_sku
            SET sku_code = #{skuCode}, name = #{name}, price = #{price}, stock = #{stock},
                enabled = #{enabled}, sort_order = #{sortOrder}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND product_id = #{productId}
            """)
    int update(ProductSkuEntity sku);

    /**
     * 删除商品规格。
     *
     * @param id 规格主键
     * @param productId 商品主键
     * @return 受影响行数
     */
    @Delete("DELETE FROM product_sku WHERE id = #{id} AND product_id = #{productId}")
    int delete(@Param("id") Long id, @Param("productId") Long productId);

    /**
     * 原子扣减可用规格库存。
     *
     * @param id 规格主键
     * @param productId 商品主键
     * @param quantity 购买数量
     * @return 受影响行数
     */
    @Update("""
            UPDATE product_sku SET stock = stock - #{quantity}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND product_id = #{productId} AND enabled = TRUE AND stock >= #{quantity}
            """)
    int decreaseStock(@Param("id") Long id, @Param("productId") Long productId,
                      @Param("quantity") int quantity);

    /**
     * 回补规格库存。
     *
     * @param id 规格主键
     * @param productId 商品主键
     * @param quantity 回补数量
     * @return 受影响行数
     */
    @Update("""
            UPDATE product_sku SET stock = stock + #{quantity}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND product_id = #{productId}
            """)
    int restoreStock(@Param("id") Long id, @Param("productId") Long productId,
                     @Param("quantity") int quantity);

    /**
     * 汇总商品启用规格库存。
     *
     * @param productId 商品主键
     * @return 启用规格库存总量
     */
    @Select("SELECT COALESCE(SUM(stock), 0) FROM product_sku WHERE product_id = #{productId} AND enabled = TRUE")
    int sumEnabledStock(Long productId);

    /**
     * 统计仍可能回补库存的规格订单明细。
     *
     * @param skuId 规格主键
     * @return 活跃订单明细数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM trade_order_item i
            JOIN trade_order o ON o.id = i.order_id
            WHERE i.sku_id = #{skuId}
              AND o.status IN ('WAIT_PAYMENT', 'PAID', 'WAIT_VERIFICATION', 'REFUNDING')
            """)
    int countRestorableOrderItems(Long skuId);
}
