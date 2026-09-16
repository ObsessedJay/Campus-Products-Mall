package com.sk.onlinemall.dashboard.mapper;

import com.sk.onlinemall.dashboard.model.DashboardAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface DashboardMapper {
    /**
     * 按统一筛选条件聚合运营指标。
     *
     * @param startAt 统计开始时间
     * @param endAt 统计结束时间
     * @param activityId 可选活动主键
     * @param productId 可选商品主键
     * @return 聚合指标
     */
    @Select("""
            <script>
            SELECT
              (SELECT COUNT(*) FROM sys_user u WHERE u.created_at &gt;= #{startAt} AND u.created_at &lt; #{endAt}) AS user_count,
              (SELECT COUNT(*) FROM product p WHERE p.created_at &gt;= #{startAt} AND p.created_at &lt; #{endAt}
                <if test='productId != null'>AND p.id = #{productId}</if>) AS product_count,
              (SELECT COUNT(*) FROM flash_activity a WHERE a.created_at &gt;= #{startAt} AND a.created_at &lt; #{endAt}
                <if test='activityId != null'>AND a.id = #{activityId}</if>
                <if test='productId != null'>AND a.product_id = #{productId}</if>) AS activity_count,
              (SELECT COUNT(*) FROM activity_reservation r JOIN flash_activity a ON a.id = r.activity_id
                WHERE r.created_at &gt;= #{startAt} AND r.created_at &lt; #{endAt}
                <if test='activityId != null'>AND r.activity_id = #{activityId}</if>
                <if test='productId != null'>AND a.product_id = #{productId}</if>) AS reservation_count,
              (SELECT COUNT(DISTINCT o.id) FROM trade_order o LEFT JOIN trade_order_item i ON i.order_id = o.id
                WHERE o.created_at &gt;= #{startAt} AND o.created_at &lt; #{endAt}
                <if test='activityId != null'>AND o.activity_id = #{activityId}</if>
                <if test='productId != null'>AND i.product_id = #{productId}</if>) AS order_count,
              (SELECT COALESCE(SUM(x.amount), 0) FROM payment_record x JOIN trade_order o ON o.id = x.order_id
                WHERE x.status = 'SUCCESS' AND x.paid_at &gt;= #{startAt} AND x.paid_at &lt; #{endAt}
                <if test='activityId != null'>AND o.activity_id = #{activityId}</if>
                <if test='productId != null'>AND EXISTS (SELECT 1 FROM trade_order_item i WHERE i.order_id = o.id AND i.product_id = #{productId})</if>) AS paid_amount,
              (SELECT COUNT(DISTINCT o.id) FROM trade_order o LEFT JOIN trade_order_item i ON i.order_id = o.id
                WHERE o.created_at &gt;= #{startAt} AND o.created_at &lt; #{endAt}
                AND o.status IN ('PAID', 'PICKED_UP', 'REFUND_PENDING', 'REFUNDED')
                <if test='activityId != null'>AND o.activity_id = #{activityId}</if>
                <if test='productId != null'>AND i.product_id = #{productId}</if>) AS pickup_eligible_count,
              (SELECT COUNT(DISTINCT o.id) FROM trade_order o LEFT JOIN trade_order_item i ON i.order_id = o.id
                WHERE o.created_at &gt;= #{startAt} AND o.created_at &lt; #{endAt} AND o.status = 'PICKED_UP'
                <if test='activityId != null'>AND o.activity_id = #{activityId}</if>
                <if test='productId != null'>AND i.product_id = #{productId}</if>) AS picked_up_count
            </script>
            """)
    DashboardAggregate aggregate(@Param("startAt") LocalDateTime startAt,
                                 @Param("endAt") LocalDateTime endAt,
                                 @Param("activityId") Long activityId,
                                 @Param("productId") Long productId);
}
