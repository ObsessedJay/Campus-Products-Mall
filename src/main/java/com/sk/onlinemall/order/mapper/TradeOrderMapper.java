package com.sk.onlinemall.order.mapper;

import com.sk.onlinemall.order.model.OrderStatus;
import com.sk.onlinemall.order.model.OrderExportRow;
import com.sk.onlinemall.order.model.PaymentRecordEntity;
import com.sk.onlinemall.order.model.PickupVerificationEntity;
import com.sk.onlinemall.order.model.RefundRecordEntity;
import com.sk.onlinemall.order.model.RefundStatus;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.order.model.TradeOrderItemEntity;
import com.sk.onlinemall.order.model.UserQuantity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface TradeOrderMapper {
    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount,
                   payment_deadline, created_at, updated_at
            FROM trade_order WHERE id = #{id}
            """)
    TradeOrderEntity findById(Long id);

    /**
     * 按业务请求号查询记录。
     *
     * @param requestNo 业务请求号
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount,
                   payment_deadline, created_at, updated_at
            FROM trade_order WHERE request_no = #{requestNo}
            """)
    TradeOrderEntity findByRequestNo(String requestNo);

    /**
     * 查询指定用户的订单。
     *
     * @param userId 用户主键
     * @param status 业务状态或连接关闭状态
     * @return 查询结果
     */
    @Select("""
            <script>
            SELECT id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount,
                   payment_deadline, created_at, updated_at
            FROM trade_order WHERE user_id = #{userId}
            <if test="status != null">AND status = #{status}</if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    List<TradeOrderEntity> findByUser(@Param("userId") Long userId, @Param("status") OrderStatus status);

    /**
     * 校验已完成订单是否包含指定商品。
     *
     * @param orderId 订单主键
     * @param userId 用户主键
     * @param productId 商品主键
     * @return 匹配记录数量
     */
    @Select("""
            SELECT COUNT(*) FROM trade_order o
            JOIN trade_order_item i ON i.order_id = o.id
            WHERE o.id = #{orderId} AND o.user_id = #{userId} AND o.status = 'COMPLETED'
              AND i.product_id = #{productId}
            """)
    int countCompletedProduct(@Param("orderId") Long orderId, @Param("userId") Long userId,
                              @Param("productId") Long productId);

    /**
     * 查询用于运营导出的订单与核销明细。
     *
     * @param activityId 可选活动主键
     * @param status 可选订单状态
     * @param pickupPointId 可选自提点主键
     * @param verified 可选核销结果，true 表示已核销，false 表示未核销
     * @param operatorId 非管理员操作人主键，用于限制授权自提点
     * @return 订单导出明细
     */
    @Select("""
            <script>
            SELECT o.id AS order_id, o.order_no, o.activity_id, o.user_id,
                   u.nickname, u.email, o.status, o.total_amount,
                   o.pickup_point_id, o.pickup_point_name, o.pickup_point_address,
                   i.product_name, i.sku_code, i.sku_name, i.unit_price, i.quantity, i.line_amount,
                   pv.pickup_code, verifier.nickname AS verifier_nickname, pv.verified_at, o.created_at
            FROM trade_order o
            JOIN sys_user u ON u.id = o.user_id
            JOIN trade_order_item i ON i.order_id = o.id
            LEFT JOIN pickup_verification pv ON pv.order_id = o.id
            LEFT JOIN sys_user verifier ON verifier.id = pv.verified_by
            WHERE 1 = 1
            <if test="activityId != null">AND o.activity_id = #{activityId}</if>
            <if test="status != null">AND o.status = #{status}</if>
            <if test="pickupPointId != null">AND o.pickup_point_id = #{pickupPointId}</if>
            <if test="verified != null and verified">AND pv.verified_at IS NOT NULL</if>
            <if test="verified != null and !verified">AND pv.verified_at IS NULL</if>
            <if test="operatorId != null">
                AND EXISTS (
                    SELECT 1 FROM operator_pickup_point opp
                    WHERE opp.operator_id = #{operatorId} AND opp.pickup_point_id = o.pickup_point_id
                )
            </if>
            ORDER BY o.created_at DESC, o.id DESC, i.id ASC
            </script>
            """)
    List<OrderExportRow> findForExport(@Param("activityId") Long activityId,
                                       @Param("status") OrderStatus status,
                                       @Param("pickupPointId") Long pickupPointId,
                                       @Param("verified") Boolean verified,
                                       @Param("operatorId") Long operatorId);

    /**
     * 查询已过期支付订单列表。
     *
     * @param now 当前业务时间
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount,
                   payment_deadline, created_at, updated_at
            FROM trade_order
            WHERE status = 'WAIT_PAYMENT'
              AND payment_deadline IS NOT NULL
              AND payment_deadline <= #{now}
            ORDER BY payment_deadline ASC, id ASC
            """)
    List<TradeOrderEntity> findExpiredPaymentOrders(LocalDateTime now);

    /**
     * 统计用户在活动中的有效购买数量。
     *
     * @param activityId 活动主键
     * @param userId 用户主键
     * @return 处理结果数量
     */
    @Select("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM trade_order o
            JOIN trade_order_item i ON i.order_id = o.id
            WHERE o.activity_id = #{activityId} AND o.user_id = #{userId}
              AND o.status NOT IN ('CANCELLED', 'REFUNDED')
            """)
    int sumActiveQuantityByActivityAndUser(@Param("activityId") Long activityId,
                                           @Param("userId") Long userId);

    /**
     * 统计活动已支付商品数量。
     *
     * @param activityId 活动主键
     * @return 处理结果数量
     */
    @Select("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM trade_order o
            JOIN trade_order_item i ON i.order_id = o.id
            WHERE o.activity_id = #{activityId}
              AND o.status IN ('PAID', 'WAIT_VERIFICATION', 'COMPLETED', 'REFUNDING')
            """)
    int sumPaidQuantityByActivity(Long activityId);

    /**
     * 按用户汇总活动有效购买数量。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    @Select("""
            SELECT o.user_id, SUM(i.quantity) AS quantity
            FROM trade_order o
            JOIN trade_order_item i ON i.order_id = o.id
            WHERE o.activity_id = #{activityId}
              AND o.status NOT IN ('CANCELLED', 'REFUNDED')
            GROUP BY o.user_id
            """)
    List<UserQuantity> findActiveQuantityByActivityGroupedByUser(Long activityId);

    /**
     * 新增交易订单。
     *
     * @param order 订单信息
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO trade_order
              (order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
               pickup_point_address, status, total_amount, payment_deadline)
            VALUES (#{orderNo}, #{requestNo}, #{userId}, #{activityId}, #{pickupPointId}, #{pickupPointName},
                    #{pickupPointAddress}, #{status}, #{totalAmount}, #{paymentDeadline})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TradeOrderEntity order);

    /**
     * 新增订单明细。
     *
     * @param item 订单明细
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO trade_order_item
              (order_id, product_id, sku_id, product_name, sku_code, sku_name, unit_price, quantity, line_amount)
            VALUES (#{orderId}, #{productId}, #{skuId}, #{productName}, #{skuCode}, #{skuName},
                    #{unitPrice}, #{quantity}, #{lineAmount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertItem(TradeOrderItemEntity item);

    /**
     * 查询订单明细。
     *
     * @param orderId 订单主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, product_id, sku_id, product_name, sku_code, sku_name,
                   unit_price, quantity, line_amount
            FROM trade_order_item WHERE order_id = #{orderId}
            """)
    List<TradeOrderItemEntity> findItems(Long orderId);

    /**
     * 更新状态。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param expectedStatus 更新前的预期状态
     * @return 数据库受影响行数
     */
    @Update("UPDATE trade_order SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status,
                     @Param("expectedStatus") OrderStatus expectedStatus);

    /**
     * 按幂等键查询支付记录。
     *
     * @param idempotencyKey 支付幂等键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, payment_no, idempotency_key, amount, status, paid_at, created_at
            FROM payment_record WHERE idempotency_key = #{idempotencyKey}
            """)
    PaymentRecordEntity findPaymentByKey(String idempotencyKey);

    /**
     * 查询订单最近一次支付记录。
     *
     * @param orderId 订单主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, payment_no, idempotency_key, amount, status, paid_at, created_at
            FROM payment_record WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1
            """)
    PaymentRecordEntity findPaymentByOrder(Long orderId);

    /**
     * 写入支付。
     *
     * @param payment 支付记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO payment_record (order_id, payment_no, idempotency_key, amount, status, paid_at)
            VALUES (#{orderId}, #{paymentNo}, #{idempotencyKey}, #{amount}, #{status}, #{paidAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertPayment(PaymentRecordEntity payment);

    /**
     * 写入自提核销验证。
     *
     * @param verification 提货核销记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO pickup_verification (order_id, pickup_code)
            VALUES (#{orderId}, #{pickupCode})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertPickupVerification(PickupVerificationEntity verification);

    /**
     * 查询订单提货核销记录。
     *
     * @param orderId 订单主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, pickup_code, verified_by, verified_at, created_at
            FROM pickup_verification WHERE order_id = #{orderId}
            """)
    PickupVerificationEntity findPickupByOrder(Long orderId);

    /**
     * 按提货码查询核销记录。
     *
     * @param pickupCode 提货核销码
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, pickup_code, verified_by, verified_at, created_at
            FROM pickup_verification WHERE pickup_code = #{pickupCode}
            """)
    PickupVerificationEntity findPickupByCode(String pickupCode);

    /**
     * 将提货记录标记为已核销。
     *
     * @param id 记录主键
     * @param verifiedBy 核销人主键
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE pickup_verification
            SET verified_by = #{verifiedBy}, verified_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND verified_at IS NULL
            """)
    int markPickupVerified(@Param("id") Long id, @Param("verifiedBy") Long verifiedBy);

    /**
     * 查询订单退款记录。
     *
     * @param orderId 订单主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, order_id, refund_no, reason, status, processed_by, processed_at,
                   process_reason, created_at, updated_at
            FROM refund_record WHERE order_id = #{orderId}
            """)
    RefundRecordEntity findRefundByOrder(Long orderId);

    /**
     * 新增退款记录。
     *
     * @param refund 退款记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO refund_record (order_id, refund_no, reason, status)
            VALUES (#{orderId}, #{refundNo}, #{reason}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRefund(RefundRecordEntity refund);

    /**
     * 按预期状态更新退款处理结果。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param processedBy 处理人主键
     * @param processReason 处理原因
     * @param expectedStatus 更新前的预期状态
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE refund_record
            SET status = #{status}, processed_by = #{processedBy}, processed_at = CURRENT_TIMESTAMP,
                process_reason = #{processReason}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int updateRefundStatus(@Param("id") Long id, @Param("status") RefundStatus status,
                           @Param("processedBy") Long processedBy, @Param("processReason") String processReason,
                           @Param("expectedStatus") RefundStatus expectedStatus);
}
