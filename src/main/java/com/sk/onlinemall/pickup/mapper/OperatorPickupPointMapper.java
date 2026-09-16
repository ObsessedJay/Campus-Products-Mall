package com.sk.onlinemall.pickup.mapper;

import com.sk.onlinemall.pickup.model.PickupPointEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperatorPickupPointMapper {
    /**
     * 查询运营人员已授权的自提点。
     *
     * @param operatorId 运营人员主键
     * @return 已授权自提点
     */
    @Select("""
            SELECT p.id, p.name, p.campus, p.address, p.longitude, p.latitude, p.opening_hours,
                   p.contact_phone, p.status, p.created_at, p.updated_at
            FROM operator_pickup_point opp
            JOIN pickup_point p ON p.id = opp.pickup_point_id
            WHERE opp.operator_id = #{operatorId}
            ORDER BY p.campus, p.id
            """)
    List<PickupPointEntity> findByOperatorId(Long operatorId);

    /**
     * 判断运营人员是否拥有指定点位权限。
     *
     * @param operatorId 运营人员主键
     * @param pickupPointId 自提点主键
     * @return 是否拥有权限
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM operator_pickup_point
            WHERE operator_id = #{operatorId} AND pickup_point_id = #{pickupPointId}
            """)
    boolean exists(@Param("operatorId") Long operatorId, @Param("pickupPointId") Long pickupPointId);

    /**
     * 删除运营人员的全部点位权限。
     *
     * @param operatorId 运营人员主键
     * @return 数据库受影响行数
     */
    @Delete("DELETE FROM operator_pickup_point WHERE operator_id = #{operatorId}")
    int deleteByOperatorId(Long operatorId);

    /**
     * 批量新增运营人员点位权限。
     *
     * @param operatorId 运营人员主键
     * @param pickupPointIds 自提点主键列表
     * @return 数据库受影响行数
     */
    @Insert("""
            <script>
            INSERT INTO operator_pickup_point (operator_id, pickup_point_id) VALUES
            <foreach collection="pickupPointIds" item="pickupPointId" separator=",">
                (#{operatorId}, #{pickupPointId})
            </foreach>
            </script>
            """)
    int insertAll(@Param("operatorId") Long operatorId,
                  @Param("pickupPointIds") List<Long> pickupPointIds);
}
