package com.sk.onlinemall.review.mapper;

import com.sk.onlinemall.review.model.ReviewContentType;
import com.sk.onlinemall.review.model.ReviewLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewLogMapper {
    /**
     * 新增内容审核日志。
     *
     * @param log 审核日志
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO content_review_log
              (content_type, content_id, reviewer_id, reviewer_username, result, reason)
            VALUES (#{contentType}, #{contentId}, #{reviewerId}, #{reviewerUsername}, #{result}, #{reason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ReviewLogEntity log);

    /**
     * 按条件查询内容审核日志。
     *
     * @param type 审核内容类型
     * @param contentId 审核内容主键
     * @param limit 最大返回数量
     * @return 查询结果
     */
    @Select("""
            <script>
            SELECT id, content_type, content_id, reviewer_id, reviewer_username,
                   result, reason, created_at
            FROM content_review_log
            <where>
              <if test="type != null">content_type = #{type}</if>
              <if test="contentId != null">AND content_id = #{contentId}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ReviewLogEntity> find(@Param("type") ReviewContentType type,
                               @Param("contentId") Long contentId,
                               @Param("limit") int limit);
}
