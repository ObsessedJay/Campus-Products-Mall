package com.sk.onlinemall.review;

import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenService tokenService;

    private String adminToken;
    private String operatorToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM content_review_log");
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (901, 'review-admin', 'unused', 'ADMIN', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (902, 'review-operator', 'unused', 'OPERATOR', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (901, 'Review Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (901, 901, 'Review Product', 'NORMAL', 'PENDING_REVIEW', ?, 10, 0, 1)
                """, new BigDecimal("9.90"));
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        LocalDateTime endAt = startAt.plusHours(2);
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, created_by, mode, status, review_status, start_at, end_at,
                   stock, limit_per_user, payment_timeout_minutes)
                VALUES (901, 'Approved Activity', 901, 1, 902, 'FLASH_SALE', 'PENDING_REVIEW', 'PENDING',
                        ?, ?, 10, 1, 15)
                """, startAt, endAt);
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, created_by, mode, status, review_status, start_at, end_at,
                   stock, limit_per_user, payment_timeout_minutes)
                VALUES (902, 'Rejected Activity', 901, 1, 902, 'FLASH_SALE', 'UNPUBLISHED', 'PENDING',
                        ?, ?, 10, 1, 15)
                """, startAt, endAt);
        adminToken = tokenService.issue(901L, "review-admin", "ADMIN");
        operatorToken = tokenService.issue(902L, "review-operator", "OPERATOR");
    }

    /**
     * 验证仅管理员可通过审核且审核日志完整保留。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldAllowOnlyAdminToApproveAndKeepReviewLog() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews").header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/reviews").header("Authorization", "Bearer " + adminToken)
                        .param("type", "PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("PRODUCT"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/admin/reviews/PRODUCT/901/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        mockMvc.perform(get("/api/v1/admin/reviews/PRODUCT/901/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].result").value("APPROVED"))
                .andExpect(jsonPath("$.data[0].reviewerUsername").value("review-admin"));
    }

    /**
     * 验证驳回审核时必须填写原因。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireReasonWhenRejecting() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reviews/PRODUCT/901/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/reviews/PRODUCT/901/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cover image is not compliant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    /**
     * 验证活动审核结果、统一日志筛选和重复审核保护。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldReviewActivitiesAndFilterBoundedLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("type", "ACTIVITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/admin/reviews/ACTIVITY/901/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/reviews/ACTIVITY/902/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"activity rules are incomplete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"));

        mockMvc.perform(get("/api/v1/admin/reviews/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("type", "ACTIVITY")
                        .param("contentId", "902")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].result").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].reason").value("activity rules are incomplete"));

        mockMvc.perform(post("/api/v1/admin/reviews/ACTIVITY/902/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"repeat\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_PENDING"));

        mockMvc.perform(get("/api/v1/admin/reviews/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REVIEW_LOG_LIMIT"));
    }
}
