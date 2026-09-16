package com.sk.onlinemall.user.mapper;

import com.sk.onlinemall.user.model.UserCreditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserGovernanceMapper {
    /**
     * 写入信用分调整日志。
     *
     * @param userId 用户主键
     * @param previous 原分值
     * @param adjusted 新分值
     * @param reason 调整原因
     * @param operatorId 操作人主键
     * @return 受影响行数
     */
    @Insert("INSERT INTO user_credit_log (user_id, previous_score, adjusted_score, change_amount, reason, operated_by) VALUES (#{userId}, #{previous}, #{adjusted}, #{adjusted} - #{previous}, #{reason}, #{operatorId})")
    int insertCreditLog(@Param("userId") Long userId, @Param("previous") int previous,
                        @Param("adjusted") int adjusted, @Param("reason") String reason,
                        @Param("operatorId") Long operatorId);

    /**
     * 查询用户信用变更记录。
     *
     * @param userId 用户主键
     * @return 信用日志
     */
    @Select("SELECT id, user_id, previous_score, adjusted_score, change_amount, reason, operated_by, created_at FROM user_credit_log WHERE user_id = #{userId} ORDER BY id DESC LIMIT 100")
    List<UserCreditLog> findCreditLogs(Long userId);

    /**
     * 统计用户预约数。
     *
     * @param userId 用户主键
     * @return 预约数
     */
    @Select("SELECT COUNT(*) FROM activity_reservation WHERE user_id = #{userId}")
    long countReservations(Long userId);

    /**
     * 统计用户订单数。
     *
     * @param userId 用户主键
     * @return 订单数
     */
    @Select("SELECT COUNT(*) FROM trade_order WHERE user_id = #{userId}")
    long countOrders(Long userId);

    /**
     * 统计用户提交和被指向的举报数。
     *
     * @param userId 用户主键
     * @return 举报数
     */
    @Select("SELECT COUNT(*) FROM report_record WHERE reporter_id = #{userId} OR (target_type = 'USER' AND target_id = #{userId})")
    long countReports(Long userId);
}
