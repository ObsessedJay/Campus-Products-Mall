package com.sk.onlinemall.order.mapper;

import com.sk.onlinemall.order.model.FlashSaleCompensationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FlashSaleCompensationMapper {
    /**
     * 新增抢购补偿审计记录。
     *
     * @param compensation 抢购补偿审计记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO flash_sale_compensation
              (request_id, request_no, operated_by, reason, previous_failure_code,
               previous_failure_message, status)
            VALUES (#{requestId}, #{requestNo}, #{operatedBy}, #{reason}, #{previousFailureCode},
                    #{previousFailureMessage}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FlashSaleCompensationEntity compensation);

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("""
            SELECT id, request_id, request_no, operated_by, reason, previous_failure_code,
                   previous_failure_message, status, failure_message, created_at, completed_at
            FROM flash_sale_compensation WHERE id = #{id}
            """)
    FlashSaleCompensationEntity findById(Long id);

    /**
     * 按业务请求号查询记录。
     *
     * @param requestNo 业务请求号
     * @return 查询结果
     */
    @Select("""
            SELECT id, request_id, request_no, operated_by, reason, previous_failure_code,
                   previous_failure_message, status, failure_message, created_at, completed_at
            FROM flash_sale_compensation
            WHERE request_no = #{requestNo}
            ORDER BY created_at DESC, id DESC
            LIMIT 20
            """)
    List<FlashSaleCompensationEntity> findByRequestNo(String requestNo);

    /**
     * 查询请求最近一条待处理补偿记录主键。
     *
     * @param requestNo 业务请求号
     * @return 查询结果
     */
    @Select("""
            SELECT id FROM flash_sale_compensation
            WHERE request_no = #{requestNo} AND status = 'PENDING'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    Long findLatestPendingId(String requestNo);

    /**
     * 完成当前业务处理并更新最终状态。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param failureMessage 失败原因说明
     * @return 处理结果数量
     */
    @Update("""
            UPDATE flash_sale_compensation
            SET status = #{status}, failure_message = #{failureMessage}, completed_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int complete(@Param("id") Long id, @Param("status") String status,
                 @Param("failureMessage") String failureMessage);
}
