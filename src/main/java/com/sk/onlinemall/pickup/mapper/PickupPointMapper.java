package com.sk.onlinemall.pickup.mapper;

import com.sk.onlinemall.pickup.model.PickupPointEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PickupPointMapper {

    /**
     * 查询启用的。
     *
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, campus, address, longitude, latitude, opening_hours, contact_phone, status, created_at, updated_at
            FROM pickup_point
            WHERE status = 'ACTIVE'
            ORDER BY campus, id
            """)
    List<PickupPointEntity> findActive();

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, name, campus, address, longitude, latitude, opening_hours, contact_phone, status, created_at, updated_at
            FROM pickup_point
            WHERE id = #{id}
            """)
    PickupPointEntity findById(Long id);
}
