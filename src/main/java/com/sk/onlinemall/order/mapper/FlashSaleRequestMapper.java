package com.sk.onlinemall.order.mapper;

import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.sk.onlinemall.order.model.UserQuantity;

import java.util.List;

@Mapper
public interface FlashSaleRequestMapper {
    /**
     * 按业务请求号查询记录。
     *
     * @param requestNo 业务请求号
     * @return 查询结果
     */
    @Select("""
            SELECT id, request_no, activity_id, product_id, sku_id, user_id, quantity, status, order_id,
                   failure_code, failure_message, created_at, updated_at
            FROM flash_sale_request WHERE request_no = #{requestNo}
            """)
    FlashSaleRequestEntity findByRequestNo(String requestNo);

    /**
     * 查询失败的。
     *
     * @param activityId 活动主键
     * @param failureCode 失败原因编码
     * @return 查询结果
     */
    @Select("""
            <script>
            SELECT id, request_no, activity_id, product_id, sku_id, user_id, quantity, status, order_id,
                   failure_code, failure_message, created_at, updated_at
            FROM flash_sale_request
            WHERE status = 'FAILED'
              <if test="activityId != null">AND activity_id = #{activityId}</if>
              <if test="failureCode != null and failureCode != ''">AND failure_code = #{failureCode}</if>
            ORDER BY updated_at DESC, id DESC
            LIMIT 50
            </script>
            """)
    List<FlashSaleRequestEntity> findFailed(@Param("activityId") Long activityId,
                                            @Param("failureCode") String failureCode);

    /**
     * 新增抢购受理请求。
     *
     * @param request 抢购受理请求
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO flash_sale_request
              (request_no, activity_id, product_id, sku_id, user_id, quantity, status)
            VALUES (#{requestNo}, #{activityId}, #{productId}, #{skuId}, #{userId}, #{quantity}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FlashSaleRequestEntity request);

    /**
     * 标记待审核列表。
     *
     * @param requestNo 业务请求号
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status = 'ACCEPTING'
            """)
    int markPending(String requestNo);

    /**
     * 将失败请求标记为补偿处理中。
     *
     * @param requestNo 业务请求号
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'COMPENSATING', updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status = 'FAILED'
            """)
    int markCompensating(String requestNo);

    /**
     * 标记待审核列表来源于补偿。
     *
     * @param requestNo 业务请求号
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'PENDING', failure_code = NULL, failure_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status = 'COMPENSATING'
            """)
    int markPendingFromCompensation(String requestNo);

    /**
     * 标记补偿失败的。
     *
     * @param requestNo 业务请求号
     * @param failureCode 失败原因编码
     * @param failureMessage 失败原因说明
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'FAILED', failure_code = #{failureCode}, failure_message = #{failureMessage},
                updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status IN ('COMPENSATING', 'PENDING')
            """)
    int markCompensationFailed(@Param("requestNo") String requestNo,
                               @Param("failureCode") String failureCode,
                               @Param("failureMessage") String failureMessage);

    /**
     * 标记成功。
     *
     * @param requestNo 业务请求号
     * @param orderId 订单主键
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'SUCCEEDED', order_id = #{orderId}, failure_code = NULL,
                failure_message = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status IN ('ACCEPTING', 'PENDING')
            """)
    int markSucceeded(@Param("requestNo") String requestNo, @Param("orderId") Long orderId);

    /**
     * 标记失败的。
     *
     * @param requestNo 业务请求号
     * @param failureCode 失败原因编码
     * @param failureMessage 失败原因说明
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_sale_request
            SET status = 'FAILED', failure_code = #{failureCode}, failure_message = #{failureMessage},
                updated_at = CURRENT_TIMESTAMP
            WHERE request_no = #{requestNo} AND status IN ('ACCEPTING', 'PENDING')
            """)
    int markFailed(@Param("requestNo") String requestNo, @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage);

    /**
     * 统计活动排队中的预扣数量。
     *
     * @param activityId 活动主键
     * @return 处理结果数量
     */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM flash_sale_request
            WHERE activity_id = #{activityId} AND status = 'PENDING'
            """)
    int sumPendingQuantity(Long activityId);

    /**
     * 按用户汇总活动排队中的预扣数量。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    @Select("""
            SELECT user_id, SUM(quantity) AS quantity
            FROM flash_sale_request
            WHERE activity_id = #{activityId} AND status = 'PENDING'
            GROUP BY user_id
            """)
    List<UserQuantity> findPendingQuantityByUser(Long activityId);

    /**
     * 统计活动正在受理的抢购请求数。
     *
     * @param activityId 活动主键
     * @return 处理结果数量
     */
    @Select("""
            SELECT COUNT(*) FROM flash_sale_request
            WHERE activity_id = #{activityId} AND status = 'ACCEPTING'
            """)
    int countAccepting(Long activityId);
}
