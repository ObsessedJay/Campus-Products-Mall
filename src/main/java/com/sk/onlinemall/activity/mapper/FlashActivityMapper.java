package com.sk.onlinemall.activity.mapper;

import com.sk.onlinemall.activity.model.ActivityReservationEntity;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.LotteryBatchEntity;
import com.sk.onlinemall.activity.model.ReservationStatus;
import com.sk.onlinemall.activity.model.ReservationRosterItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface FlashActivityMapper {
    /**
     * 查询公开数据。
     *
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity
            WHERE status NOT IN ('UNPUBLISHED', 'PENDING_REVIEW', 'REJECTED', 'TERMINATED')
            ORDER BY start_at ASC, id ASC
            """)
    List<FlashActivityEntity> findPublic();

    /**
     * 查询运营侧全部活动。
     *
     * @return 全部活动
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity
            ORDER BY created_at DESC, id DESC
            """)
    List<FlashActivityEntity> findForManagement();

    /**
     * 查询需要同步状态的活动。
     *
     * @param now 当前业务时间
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity
            WHERE (status = 'RESERVING' AND reservation_end_at IS NOT NULL AND reservation_end_at <= #{now})
               OR (status IN ('RESERVING', 'PENDING') AND start_at <= #{now})
               OR (status IN ('RESERVING', 'PENDING', 'RUNNING') AND end_at <= #{now})
            ORDER BY start_at ASC, id ASC
            """)
    List<FlashActivityEntity> findNeedingStateSync(LocalDateTime now);

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity WHERE id = #{id}
            """)
    FlashActivityEntity findById(Long id);

    /**
     * 按主键查询并锁定活动记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity WHERE id = #{id} FOR UPDATE
            """)
    FlashActivityEntity findByIdForUpdate(Long id);

    /**
     * 新增抢购活动。
     *
     * @param activity 活动信息
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO flash_activity
              (name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
               start_at, end_at, stock, limit_per_user, payment_timeout_minutes, rule_description)
            VALUES (#{name}, #{productId}, #{pickupPointId}, #{createdBy}, #{mode}, #{status}, #{reviewStatus}, #{reservationStartAt}, #{reservationEndAt},
                    #{startAt}, #{endAt}, #{stock}, #{limitPerUser}, #{paymentTimeoutMinutes}, #{ruleDescription})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FlashActivityEntity activity);

    /**
     * 更新仍处于可编辑状态的活动并重新送审。
     *
     * @param activity 活动信息
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_activity
            SET name = #{name}, product_id = #{productId}, pickup_point_id = #{pickupPointId}, mode = #{mode},
                status = #{status}, review_status = #{reviewStatus},
                reservation_start_at = #{reservationStartAt}, reservation_end_at = #{reservationEndAt},
                start_at = #{startAt}, end_at = #{endAt}, stock = #{stock},
                limit_per_user = #{limitPerUser}, payment_timeout_minutes = #{paymentTimeoutMinutes},
                rule_description = #{ruleDescription}, terminate_reason = NULL, terminated_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status IN ('UNPUBLISHED', 'PENDING_REVIEW', 'REJECTED')
            """)
    int updateForManagement(FlashActivityEntity activity);

    /**
     * 更新状态。
     *
     * @param activity 活动信息
     * @return 数据库受影响行数
     */
    @Update("UPDATE flash_activity SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(FlashActivityEntity activity);

    /**
     * 仅在当前状态匹配时更新活动状态。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param expectedStatus 更新前的预期状态
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_activity SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int updateStatusIfCurrent(@Param("id") Long id, @Param("status") ActivityStatus status,
                              @Param("expectedStatus") ActivityStatus expectedStatus);

    /**
     * 更新审核状态。
     *
     * @param activity 活动信息
     * @param expectedStatus 审核前的预期状态
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_activity
            SET status = #{activity.status}, review_status = #{activity.reviewStatus}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{activity.id} AND status = #{expectedStatus}
              AND (review_status = 'PENDING' OR review_status IS NULL)
            """)
    int updateReviewStatus(@Param("activity") FlashActivityEntity activity,
                           @Param("expectedStatus") ActivityStatus expectedStatus);

    /**
     * 查询待审核列表审核。
     *
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, product_id, pickup_point_id, created_by, mode, status, review_status, reservation_start_at, reservation_end_at,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes,
                   rule_description, terminate_reason, terminated_at, created_at, updated_at
            FROM flash_activity
            WHERE status IN ('UNPUBLISHED', 'PENDING_REVIEW', 'REJECTED')
              AND (review_status = 'PENDING' OR review_status IS NULL)
            ORDER BY created_at ASC, id ASC
            """)
    List<FlashActivityEntity> findPendingReview();

    /**
     * 终止。
     *
     * @param id 记录主键
     * @param reason 操作原因
     * @return 处理后的业务数据
     */
    @Update("""
            UPDATE flash_activity
            SET status = 'TERMINATED', terminate_reason = #{reason},
                terminated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status NOT IN ('ENDED', 'TERMINATED')
            """)
    int terminate(@Param("id") Long id, @Param("reason") String reason);

    /**
     * 查询预约。
     *
     * @param activityId 活动主键
     * @param userId 用户主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, activity_id, user_id, reservation_no, status, lottery_batch_id, draw_rank,
                   created_at, updated_at
            FROM activity_reservation WHERE activity_id = #{activityId} AND user_id = #{userId}
            """)
    ActivityReservationEntity findReservation(@Param("activityId") Long activityId,
                                              @Param("userId") Long userId);

    /**
     * 写入预约。
     *
     * @param reservation 预约参数
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO activity_reservation (activity_id, user_id, reservation_no, status)
            VALUES (#{activityId}, #{userId}, #{reservationNo}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertReservation(ActivityReservationEntity reservation);

    /**
     * 查询活动预约记录。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, activity_id, user_id, reservation_no, status, lottery_batch_id, draw_rank,
                   created_at, updated_at
            FROM activity_reservation
            WHERE activity_id = #{activityId} AND status != 'CANCELLED'
            ORDER BY id ASC
            """)
    List<ActivityReservationEntity> findReservations(Long activityId);

    /**
     * 查询运营侧活动预约名单。
     *
     * @param activityId 活动主键
     * @param status 可选预约状态
     * @return 包含学生身份和抽签结果的预约名单
     */
    @Select("""
            <script>
            SELECT ar.id, ar.activity_id, fa.name AS activity_name, ar.user_id,
                   u.email, u.nickname, ar.reservation_no, ar.status,
                   lb.batch_no AS lottery_batch_no, ar.draw_rank, ar.created_at, ar.updated_at
            FROM activity_reservation ar
            JOIN flash_activity fa ON fa.id = ar.activity_id
            JOIN sys_user u ON u.id = ar.user_id
            LEFT JOIN lottery_batch lb ON lb.id = ar.lottery_batch_id
            WHERE ar.activity_id = #{activityId}
            <if test="status != null">
                AND ar.status = #{status}
            </if>
            ORDER BY ar.created_at ASC, ar.id ASC
            </script>
            """)
    List<ReservationRosterItem> findReservationRoster(
            @Param("activityId") Long activityId,
            @Param("status") ReservationStatus status);

    /**
     * 查询活动抽签批次。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, activity_id, batch_no, random_seed, total_reservations, winner_count,
                   drawn_by, drawn_at, created_at
            FROM lottery_batch WHERE activity_id = #{activityId}
            """)
    LotteryBatchEntity findLotteryBatch(Long activityId);

    /**
     * 新增抽签批次。
     *
     * @param batch 抽签批次
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO lottery_batch
              (activity_id, batch_no, random_seed, total_reservations, winner_count, drawn_by, drawn_at)
            VALUES (#{activityId}, #{batchNo}, #{randomSeed}, #{totalReservations}, #{winnerCount},
                    #{drawnBy}, #{drawnAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertLotteryBatch(LotteryBatchEntity batch);

    /**
     * 更新预约结果。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param batchId 抽签批次主键
     * @param drawRank 抽签名次
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE activity_reservation
            SET status = #{status}, lottery_batch_id = #{batchId}, draw_rank = #{drawRank},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int updateReservationResult(@Param("id") Long id, @Param("status") ReservationStatus status,
                                @Param("batchId") Long batchId, @Param("drawRank") Integer drawRank);

    /**
     * 扣减活动库存。
     *
     * @param id 记录主键
     * @param quantity 购买数量
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE flash_activity
            SET stock = stock - #{quantity}, status = 'RUNNING', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND stock >= #{quantity}
              AND status NOT IN ('ENDED', 'TERMINATED', 'UNPUBLISHED', 'PENDING_REVIEW', 'REJECTED')
            """)
    int decreaseActivityStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 恢复活动库存。
     *
     * @param id 记录主键
     * @param quantity 购买数量
     * @return 数据库受影响行数
     */
    @Update("UPDATE flash_activity SET stock = stock + #{quantity}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int restoreActivityStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
