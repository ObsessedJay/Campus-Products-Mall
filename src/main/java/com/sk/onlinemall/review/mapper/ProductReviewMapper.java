package com.sk.onlinemall.review.mapper;

import com.sk.onlinemall.review.model.ProductReviewEntity;
import com.sk.onlinemall.review.model.ProductReviewRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductReviewMapper {
    /**
     * 新增商品评价。
     *
     * @param review 评价内容
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO product_review (order_id, product_id, user_id, rating, content, status)
            VALUES (#{orderId}, #{productId}, #{userId}, #{rating}, #{content}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProductReviewEntity review);

    /**
     * 新增评价图片地址。
     *
     * @param reviewId 评价主键
     * @param imageUrl 图片地址
     * @param sortOrder 排序序号
     * @return 数据库受影响行数
     */
    @Insert("INSERT INTO product_review_image (review_id, image_url, sort_order) VALUES (#{reviewId}, #{imageUrl}, #{sortOrder})")
    int insertImage(@Param("reviewId") Long reviewId, @Param("imageUrl") String imageUrl,
                    @Param("sortOrder") int sortOrder);

    /**
     * 按订单与商品查询评价。
     *
     * @param orderId 订单主键
     * @param productId 商品主键
     * @return 评价记录
     */
    @Select("""
            SELECT id, order_id, product_id, user_id, rating, content, status, created_at, updated_at
            FROM product_review WHERE order_id = #{orderId} AND product_id = #{productId}
            """)
    ProductReviewEntity findByOrderAndProduct(@Param("orderId") Long orderId,
                                               @Param("productId") Long productId);

    /**
     * 按主键查询评价。
     *
     * @param id 评价主键
     * @return 评价记录
     */
    @Select("""
            SELECT id, order_id, product_id, user_id, rating, content, status, created_at, updated_at
            FROM product_review WHERE id = #{id}
            """)
    ProductReviewEntity findById(Long id);

    /**
     * 查询商品公开评价。
     *
     * @param productId 商品主键
     * @return 公开评价列表
     */
    @Select("""
            SELECT r.id, r.product_id, COALESCE(u.nickname, '校园用户') AS nickname,
                   r.rating, r.content, r.created_at
            FROM product_review r
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.product_id = #{productId} AND r.status = 'VISIBLE'
            ORDER BY r.created_at DESC, r.id DESC
            """)
    List<ProductReviewRow> findVisibleByProduct(Long productId);

    /**
     * 查询评价图片。
     *
     * @param reviewId 评价主键
     * @return 图片地址列表
     */
    @Select("SELECT image_url FROM product_review_image WHERE review_id = #{reviewId} ORDER BY sort_order, id")
    List<String> findImageUrls(Long reviewId);

    /**
     * 隐藏被确认违规的评价。
     *
     * @param id 评价主键
     * @return 数据库受影响行数
     */
    @Update("UPDATE product_review SET status = 'HIDDEN', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'VISIBLE'")
    int hide(Long id);
}
