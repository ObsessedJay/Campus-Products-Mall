package com.sk.onlinemall.review.mapper;

import com.sk.onlinemall.review.model.ReportRecordEntity;
import com.sk.onlinemall.review.model.ReportStatus;
import com.sk.onlinemall.review.model.ReportTargetType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportRecordMapper {
    /**
     * 新增举报。
     *
     * @param report 举报记录
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO report_record (reporter_id, target_type, target_id, reason, status)
            VALUES (#{reporterId}, #{targetType}, #{targetId}, #{reason}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ReportRecordEntity report);

    /**
     * 查询同一用户对目标的举报。
     *
     * @param reporterId 举报人主键
     * @param targetType 目标类型
     * @param targetId 目标主键
     * @return 举报记录
     */
    @Select("""
            SELECT id, reporter_id, target_type, target_id, reason, status, handled_by,
                   handle_result, handled_at, created_at, updated_at
            FROM report_record
            WHERE reporter_id = #{reporterId} AND target_type = #{targetType} AND target_id = #{targetId}
            """)
    ReportRecordEntity findExisting(@Param("reporterId") Long reporterId,
                                    @Param("targetType") ReportTargetType targetType,
                                    @Param("targetId") Long targetId);

    /**
     * 按主键查询举报。
     *
     * @param id 举报主键
     * @return 举报记录
     */
    @Select("""
            SELECT id, reporter_id, target_type, target_id, reason, status, handled_by,
                   handle_result, handled_at, created_at, updated_at
            FROM report_record WHERE id = #{id}
            """)
    ReportRecordEntity findById(Long id);

    /**
     * 查询举报治理队列。
     *
     * @param status 可选处理状态
     * @return 举报列表
     */
    @Select("""
            <script>
            SELECT rr.id, rr.reporter_id, reporter.nickname AS reporter_nickname,
                   rr.target_type, rr.target_id,
                   CASE WHEN rr.target_type = 'PRODUCT' THEN p.name ELSE pr.content END AS target_summary,
                   rr.reason, rr.status, rr.handled_by, handler.nickname AS handler_nickname,
                   rr.handle_result, rr.handled_at, rr.created_at, rr.updated_at
            FROM report_record rr
            JOIN sys_user reporter ON reporter.id = rr.reporter_id
            LEFT JOIN sys_user handler ON handler.id = rr.handled_by
            LEFT JOIN product p ON rr.target_type = 'PRODUCT' AND p.id = rr.target_id
            LEFT JOIN product_review pr ON rr.target_type = 'REVIEW' AND pr.id = rr.target_id
            <if test="status != null">WHERE rr.status = #{status}</if>
            ORDER BY CASE WHEN rr.status = 'PENDING' THEN 0 ELSE 1 END, rr.created_at ASC, rr.id ASC
            </script>
            """)
    List<ReportRecordEntity> findAll(ReportStatus status);

    /**
     * 按预期状态处理举报。
     *
     * @param id 举报主键
     * @param status 处理结果状态
     * @param handledBy 管理员主键
     * @param result 处理说明
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE report_record
            SET status = #{status}, handled_by = #{handledBy}, handle_result = #{result},
                handled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int handle(@Param("id") Long id, @Param("status") ReportStatus status,
               @Param("handledBy") Long handledBy, @Param("result") String result);
}
