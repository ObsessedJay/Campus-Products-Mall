package com.sk.onlinemall.product.mapper;

import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper {
    /**
     * 查询用于搜索索引的全部商品快照。
     *
     * @return 商品快照
     */
    @Select("SELECT id, category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type, status, price, stock, sold_count, limit_per_user, created_at, updated_at FROM product ORDER BY id")
    List<ProductEntity> findAllForIndex();

    /**
     * 分页查询符合条件的在售商品。
     *
     * @param keyword 商品搜索关键词
     * @param categoryId 分类主键
     * @param saleType 发售Type参数
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sort 排序方式
     * @param offset 分页偏移量
     * @param limit 限制次数
     * @return 查询结果
     */
    @Select("""
            <script>
            SELECT id, category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type,
                   status, price, stock, sold_count, limit_per_user, created_at, updated_at
            FROM product
            WHERE status = 'ON_SALE'
              <if test="keyword != null">AND (name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))</if>
              <if test="categoryId != null">AND category_id = #{categoryId}</if>
              <if test="saleType != null">AND sale_type = #{saleType}</if>
              <if test="minPrice != null">AND price &gt;= #{minPrice}</if>
              <if test="maxPrice != null">AND price &lt;= #{maxPrice}</if>
            ORDER BY
              <choose>
                <when test="sort == 'priceAsc'">price ASC, id DESC</when>
                <when test="sort == 'priceDesc'">price DESC, id DESC</when>
                <when test="sort == 'sales'">sold_count DESC, id DESC</when>
                <otherwise>id DESC</otherwise>
              </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProductEntity> findOnSale(@Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId,
                                   @Param("saleType") String saleType,
                                   @Param("minPrice") java.math.BigDecimal minPrice,
                                   @Param("maxPrice") java.math.BigDecimal maxPrice,
                                   @Param("sort") String sort,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /**
     * 统计符合条件的在售商品数量。
     *
     * @param keyword 商品搜索关键词
     * @param categoryId 分类主键
     * @param saleType 发售Type参数
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @return 处理结果数量
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM product
            WHERE status = 'ON_SALE'
              <if test="keyword != null">AND (name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))</if>
              <if test="categoryId != null">AND category_id = #{categoryId}</if>
              <if test="saleType != null">AND sale_type = #{saleType}</if>
              <if test="minPrice != null">AND price &gt;= #{minPrice}</if>
              <if test="maxPrice != null">AND price &lt;= #{maxPrice}</if>
            </script>
            """)
    long countOnSale(@Param("keyword") String keyword,
                     @Param("categoryId") Long categoryId,
                     @Param("saleType") String saleType,
                     @Param("minPrice") java.math.BigDecimal minPrice,
                     @Param("maxPrice") java.math.BigDecimal maxPrice);

    /**
     * 分页查询运营侧商品目录。
     *
     * @param keyword 商品搜索关键词
     * @param status 商品状态
     * @param offset 分页偏移量
     * @param limit 每页数量
     * @return 商品列表
     */
    @Select("""
            <script>
            SELECT id, category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type,
                   status, price, stock, sold_count, limit_per_user, created_at, updated_at
            FROM product
            WHERE 1 = 1
              <if test="keyword != null">AND (name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))</if>
              <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProductEntity> findForManagement(@Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /**
     * 统计运营侧商品目录数量。
     *
     * @param keyword 商品搜索关键词
     * @param status 商品状态
     * @return 商品数量
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM product
            WHERE 1 = 1
              <if test="keyword != null">AND (name LIKE CONCAT('%', #{keyword}, '%')
                   OR subtitle LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))</if>
              <if test="status != null">AND status = #{status}</if>
            </script>
            """)
    long countForManagement(@Param("keyword") String keyword, @Param("status") String status);

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type,
                   status, price, stock, sold_count, limit_per_user, created_at, updated_at
            FROM product WHERE id = #{id}
            """)
    ProductEntity findById(Long id);

    /**
     * 查询待审核列表审核。
     *
     * @return 查询结果
     */
    @Select("""
            SELECT id, category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type,
                   status, price, stock, sold_count, limit_per_user, created_at, updated_at
            FROM product WHERE status = 'PENDING_REVIEW'
            ORDER BY created_at ASC, id ASC
            """)
    List<ProductEntity> findPendingReview();

    /**
     * 更新审核状态。
     *
     * @param product 商品信息
     * @return 数据库受影响行数
     */
    @Update("UPDATE product SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING_REVIEW'")
    int updateReviewStatus(ProductEntity product);

    /**
     * 更新运营侧商品资料和库存。
     *
     * @param product 商品信息
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE product
            SET category_id = #{categoryId}, pickup_point_id = #{pickupPointId}, name = #{name}, subtitle = #{subtitle},
                description = #{description}, sale_type = #{saleType}, status = #{status},
                price = #{price}, stock = #{stock}, limit_per_user = #{limitPerUser},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateForManagement(ProductEntity product);

    /**
     * 按预期状态更新商品生命周期状态。
     *
     * @param productId 商品主键
     * @param status 目标状态
     * @param expectedStatus 预期当前状态
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE product SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{productId} AND status = #{expectedStatus}
            """)
    int updateStatusIfCurrent(@Param("productId") Long productId, @Param("status") ProductStatus status,
                              @Param("expectedStatus") ProductStatus expectedStatus);

    /**
     * 统计引用商品的订单明细数量。
     *
     * @param productId 商品主键
     * @return 订单明细数量
     */
    @Select("SELECT COUNT(*) FROM trade_order_item WHERE product_id = #{productId}")
    int countOrderItems(Long productId);

    /**
     * 统计引用商品的活动数量。
     *
     * @param productId 商品主键
     * @return 活动数量
     */
    @Select("SELECT COUNT(*) FROM flash_activity WHERE product_id = #{productId}")
    int countActivities(Long productId);

    /**
     * 删除商品关联的收藏记录。
     *
     * @param productId 商品主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM user_favorite WHERE product_id = #{productId}")
    int deleteFavorites(Long productId);

    /**
     * 删除不再被业务记录引用的商品。
     *
     * @param productId 商品主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM product WHERE id = #{productId}")
    int delete(Long productId);

    /**
     * 原子扣减商品库存。
     *
     * @param productId 商品主键
     * @param quantity 购买数量
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE product SET stock = stock - #{quantity}, sold_count = sold_count + #{quantity},
                               updated_at = CURRENT_TIMESTAMP
            WHERE id = #{productId} AND status = 'ON_SALE' AND stock >= #{quantity}
            """)
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 恢复商品库存并回退销量。
     *
     * @param productId 商品主键
     * @param quantity 购买数量
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE product SET stock = stock + #{quantity}, sold_count = sold_count - #{quantity},
                               updated_at = CURRENT_TIMESTAMP
            WHERE id = #{productId} AND sold_count >= #{quantity}
            """)
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 同步规格汇总库存并按需重新进入审核。
     *
     * @param productId 商品主键
     * @param stock 汇总库存，空值表示保留当前库存
     * @param contentChanged 是否发生需审核的内容变化
     * @return 受影响行数
     */
    @Update("""
            UPDATE product
            SET stock = COALESCE(#{stock}, stock),
                status = CASE WHEN #{contentChanged} THEN 'PENDING_REVIEW' ELSE status END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{productId}
            """)
    int syncSkuSummary(@Param("productId") Long productId, @Param("stock") Integer stock,
                       @Param("contentChanged") boolean contentChanged);

    /**
     * 新增文创商品。
     *
     * @param product 商品信息
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO product (category_id, created_by, pickup_point_id, name, subtitle, description, cover_url, sale_type,
                                 status, price, stock, sold_count, limit_per_user)
            VALUES (#{categoryId}, #{createdBy}, #{pickupPointId}, #{name}, #{subtitle}, #{description}, #{coverUrl}, #{saleType},
                    #{status}, #{price}, #{stock}, 0, #{limitPerUser})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductEntity product);

    /**
     * 更新商品封面地址。
     *
     * @param productId 商品主键
     * @param coverUrl 封面访问地址
     * @return 数据库受影响行数
     */
    @Update("UPDATE product SET cover_url = #{coverUrl}, updated_at = CURRENT_TIMESTAMP WHERE id = #{productId}")
    int updateCoverUrl(@Param("productId") Long productId, @Param("coverUrl") String coverUrl);
}
