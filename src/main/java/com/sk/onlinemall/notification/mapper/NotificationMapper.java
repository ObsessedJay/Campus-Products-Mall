package com.sk.onlinemall.notification.mapper;

import com.sk.onlinemall.notification.model.NoticeEntity;
import com.sk.onlinemall.notification.model.UserMessageView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {
    /**
     * 按业务键查询通知。
     *
     * @param businessKey 通知业务键
     * @return 通知记录
     */
    @Select("""
            SELECT id, notice_type, business_key, title, content, link, created_at
            FROM notice WHERE business_key = #{businessKey}
            """)
    NoticeEntity findNoticeByBusinessKey(String businessKey);

    /**
     * 新增通知正文。
     *
     * @param notice 通知内容
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO notice (notice_type, business_key, title, content, link)
            VALUES (#{noticeType}, #{businessKey}, #{title}, #{content}, #{link})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertNotice(NoticeEntity notice);

    /**
     * 新增用户消息关系。
     *
     * @param noticeId 通知主键
     * @param userId 用户主键
     * @return 数据库受影响行数
     */
    @Insert("INSERT INTO user_message (notice_id, user_id) VALUES (#{noticeId}, #{userId})")
    int insertUserMessage(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 查询用户消息列表。
     *
     * @param userId 用户主键
     * @param unreadOnly 是否仅查询未读消息
     * @return 用户消息列表
     */
    @Select("""
            <script>
            SELECT um.id, n.notice_type AS type, n.title, n.content, n.link,
                   um.read_at, um.created_at
            FROM user_message um
            JOIN notice n ON n.id = um.notice_id
            WHERE um.user_id = #{userId}
            <if test="unreadOnly">AND um.read_at IS NULL</if>
            ORDER BY um.created_at DESC, um.id DESC
            LIMIT 100
            </script>
            """)
    List<UserMessageView> findByUser(@Param("userId") Long userId,
                                     @Param("unreadOnly") boolean unreadOnly);

    /**
     * 统计用户未读消息。
     *
     * @param userId 用户主键
     * @return 未读数量
     */
    @Select("SELECT COUNT(*) FROM user_message WHERE user_id = #{userId} AND read_at IS NULL")
    int countUnread(Long userId);

    /**
     * 标记一条用户消息为已读。
     *
     * @param id 用户消息主键
     * @param userId 用户主键
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE user_message SET read_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND user_id = #{userId} AND read_at IS NULL
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 将用户全部消息标记为已读。
     *
     * @param userId 用户主键
     * @return 数据库受影响行数
     */
    @Update("UPDATE user_message SET read_at = CURRENT_TIMESTAMP WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(Long userId);
}
