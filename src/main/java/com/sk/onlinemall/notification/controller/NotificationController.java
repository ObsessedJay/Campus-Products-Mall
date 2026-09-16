package com.sk.onlinemall.notification.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.notification.model.UserMessageView;
import com.sk.onlinemall.notification.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me/messages")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * 创建站内消息控制器。
     *
     * @param notificationService 站内消息服务
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 查询当前用户站内消息。
     *
     * @param unreadOnly 是否仅查询未读
     * @param authentication 当前登录身份
     * @return 用户消息列表
     */
    @GetMapping
    public ApiResponse<List<UserMessageView>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        return ApiResponse.success(notificationService.findMine(authentication.getName(), unreadOnly));
    }

    /**
     * 查询当前用户未读数量。
     *
     * @param authentication 当前登录身份
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(Authentication authentication) {
        return ApiResponse.success(Map.of("count", notificationService.countUnread(authentication.getName())));
    }

    /**
     * 标记一条消息为已读。
     *
     * @param id 用户消息主键
     * @param authentication 当前登录身份
     * @return 空成功响应
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(id, authentication.getName());
        return ApiResponse.success(null);
    }

    /**
     * 将全部消息标记为已读。
     *
     * @param authentication 当前登录身份
     * @return 更新数量
     */
    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(Authentication authentication) {
        return ApiResponse.success(Map.of("updated", notificationService.markAllRead(authentication.getName())));
    }
}
