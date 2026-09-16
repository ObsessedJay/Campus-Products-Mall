package com.sk.onlinemall.notification.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.notification.mapper.NotificationMapper;
import com.sk.onlinemall.notification.model.UserMessageView;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    /**
     * 创建站内消息查询服务。
     *
     * @param notificationMapper 通知数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询当前用户站内消息。
     *
     * @param username 当前用户名
     * @param unreadOnly 是否仅返回未读消息
     * @return 用户消息列表
     */
    public List<UserMessageView> findMine(String username, boolean unreadOnly) {
        return notificationMapper.findByUser(requireUser(username).getId(), unreadOnly);
    }

    /**
     * 查询当前用户未读消息数量。
     *
     * @param username 当前用户名
     * @return 未读消息数量
     */
    public int countUnread(String username) {
        return notificationMapper.countUnread(requireUser(username).getId());
    }

    /**
     * 标记当前用户的一条消息为已读。
     *
     * @param id 用户消息主键
     * @param username 当前用户名
     */
    public void markRead(Long id, String username) {
        UserEntity user = requireUser(username);
        if (notificationMapper.markRead(id, user.getId()) == 0
                && notificationMapper.findByUser(user.getId(), false).stream().noneMatch(item -> item.id().equals(id))) {
            throw new BusinessException("MESSAGE_NOT_FOUND", "message not found");
        }
    }

    /**
     * 将当前用户全部消息标记为已读。
     *
     * @param username 当前用户名
     * @return 本次更新数量
     */
    public int markAllRead(String username) {
        return notificationMapper.markAllRead(requireUser(username).getId());
    }

    /**
     * 查询并校验当前用户。
     *
     * @param username 当前用户名
     * @return 用户实体
     */
    private UserEntity requireUser(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "user not found");
        }
        return user;
    }
}
