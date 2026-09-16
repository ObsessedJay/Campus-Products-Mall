package com.sk.onlinemall.notification;

import com.sk.onlinemall.notification.model.NotificationMessage;
import com.sk.onlinemall.notification.model.NotificationType;
import com.sk.onlinemall.notification.service.NotificationConsumer;
import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenService tokenService;
    @Autowired private NotificationConsumer notificationConsumer;

    private String studentToken;
    private String otherToken;

    /**
     * 准备通知测试用户。
     */
    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("INSERT INTO sys_user (id, username, nickname, password_hash, role, status) VALUES (701, 'message-student', '消息同学', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, nickname, password_hash, role, status) VALUES (702, 'other-message-student', '其他同学', 'unused', 'STUDENT', 'ACTIVE')");
        studentToken = tokenService.issue(701L, "message-student", "STUDENT");
        otherToken = tokenService.issue(702L, "other-message-student", "STUDENT");
    }

    /**
     * 清理通知测试数据。
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_message");
        jdbcTemplate.update("DELETE FROM notice");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (701, 702)");
    }

    /**
     * 验证重复队列消息只生成一条站内通知和用户消息。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldConsumeNotificationIdempotentlyAndExposeUnreadCount() throws Exception {
        NotificationMessage message = new NotificationMessage(
                701L, NotificationType.PAYMENT_SUCCESS, "payment:PAY-701",
                "支付成功，等待领取", "订单 O-701 已支付。", "/orders?orderId=701");
        notificationConsumer.consume(message);
        notificationConsumer.consume(message);

        mockMvc.perform(get("/api/v1/users/me/messages")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("PAYMENT_SUCCESS"))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist());
        mockMvc.perform(get("/api/v1/users/me/messages/unread-count")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/v1/users/me/messages")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * 验证单条和全部已读操作仅影响当前用户消息。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldMarkOwnedMessagesReadAndRejectForeignMessage() throws Exception {
        notificationConsumer.consume(new NotificationMessage(
                701L, NotificationType.LOTTERY_RESULT, "lottery:1:701",
                "抽签结果已公布", "你已获得购买资格。", "/activities/1"));
        notificationConsumer.consume(new NotificationMessage(
                701L, NotificationType.ACTIVITY_REMINDER, "activity-start:1:701",
                "活动已经开售", "活动已进入发售时间。", "/activities/1"));
        Long firstId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM user_message WHERE user_id = 701", Long.class);

        mockMvc.perform(post("/api/v1/users/me/messages/{id}/read", firstId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/users/me/messages/{id}/read", firstId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/messages/unread-count")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(post("/api/v1/users/me/messages/read-all")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
    }
}
