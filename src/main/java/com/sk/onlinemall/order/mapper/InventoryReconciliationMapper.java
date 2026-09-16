package com.sk.onlinemall.order.mapper;

import com.sk.onlinemall.order.model.InventoryReconciliationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InventoryReconciliationMapper {
    /**
     * 新增库存校准审计记录。
     *
     * @param reconciliation 库存校准记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO inventory_reconciliation
              (activity_id, database_stock, redis_stock_before, redis_stock_after,
               pending_quantity, difference_before, reason, adjusted_by, status)
            VALUES (#{activityId}, #{databaseStock}, #{redisStockBefore}, #{redisStockAfter},
                    #{pendingQuantity}, #{differenceBefore}, #{reason}, #{adjustedBy}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InventoryReconciliationEntity reconciliation);

    /**
     * 查询最近的库存校准记录。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, activity_id, database_stock, redis_stock_before, redis_stock_after,
                   pending_quantity, difference_before, reason, adjusted_by, status, failure_message, created_at
            FROM inventory_reconciliation
            WHERE activity_id = #{activityId}
            ORDER BY created_at DESC, id DESC
            LIMIT 20
            """)
    List<InventoryReconciliationEntity> findRecent(Long activityId);

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, activity_id, database_stock, redis_stock_before, redis_stock_after,
                   pending_quantity, difference_before, reason, adjusted_by, status, failure_message, created_at
            FROM inventory_reconciliation WHERE id = #{id}
            """)
    InventoryReconciliationEntity findById(Long id);

    /**
     * 更新结果。
     *
     * @param reconciliation 库存校准记录
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE inventory_reconciliation
            SET status = #{status}, failure_message = #{failureMessage}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int updateResult(InventoryReconciliationEntity reconciliation);
}
