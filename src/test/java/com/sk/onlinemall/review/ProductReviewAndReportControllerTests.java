package com.sk.onlinemall.review;

import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductReviewAndReportControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenService tokenService;

    private String studentToken;
    private String otherStudentToken;
    private String adminToken;

    /**
     * 准备评价与举报测试数据。
     */
    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("INSERT INTO sys_user (id, username, nickname, password_hash, role, status) VALUES (801, 'review-student', '小成', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, nickname, password_hash, role, status) VALUES (802, 'other-student', '小信', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, nickname, password_hash, role, status) VALUES (803, 'governance-admin', '管理员', 'unused', 'ADMIN', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (801, '评价分类', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (801, 801, '银杏徽章', 'NORMAL', 'ON_SALE', ?, 10, 1, 1)
                """, new BigDecimal("19.90"));
        jdbcTemplate.update("""
                INSERT INTO trade_order
                  (id, order_no, request_no, user_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount)
                VALUES (801, 'O-801', 'R-801', 801, 1, '航空港校区一食堂', '一食堂入口', 'COMPLETED', ?)
                """, new BigDecimal("19.90"));
        jdbcTemplate.update("""
                INSERT INTO trade_order_item
                  (id, order_id, product_id, product_name, unit_price, quantity, line_amount)
                VALUES (801, 801, 801, '银杏徽章', ?, 1, ?)
                """, new BigDecimal("19.90"), new BigDecimal("19.90"));
        studentToken = tokenService.issue(801L, "review-student", "STUDENT");
        otherStudentToken = tokenService.issue(802L, "other-student", "STUDENT");
        adminToken = tokenService.issue(803L, "governance-admin", "ADMIN");
    }

    /**
     * 按外键依赖顺序清理评价治理测试数据。
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM report_record");
        jdbcTemplate.update("DELETE FROM product_review_image");
        jdbcTemplate.update("DELETE FROM product_review");
        jdbcTemplate.update("DELETE FROM pickup_verification");
        jdbcTemplate.update("DELETE FROM payment_record");
        jdbcTemplate.update("DELETE FROM trade_order_item");
        jdbcTemplate.update("DELETE FROM trade_order");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product_image");
        jdbcTemplate.update("DELETE FROM user_favorite");
        jdbcTemplate.update("DELETE FROM flash_sale_request");
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM operator_pickup_point");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (801, 802, 803)");
    }

    /**
     * 验证仅订单本人可评价已完成商品且不能重复评价。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCreateReviewOnlyForOwnedCompletedOrderOnce() throws Exception {
        String body = """
                {"productId":801,"rating":5,"content":"做工扎实，校徽细节清楚。",
                 "imageUrls":["/api/v1/files/images/reviews/sample.webp"]}
                """;
        mockMvc.perform(post("/api/v1/orders/801/reviews")
                        .header("Authorization", "Bearer " + otherStudentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_ORDER_NOT_ELIGIBLE"));

        mockMvc.perform(post("/api/v1/orders/801/reviews")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.nickname").value("小成"))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("/api/v1/files/images/reviews/sample.webp"));

        mockMvc.perform(post("/api/v1/orders/801/reviews")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        mockMvc.perform(get("/api/v1/products/801/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("做工扎实，校徽细节清楚。"));
    }

    /**
     * 验证举报幂等、管理员权限与违规评价隐藏闭环。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldResolveReviewReportAndHideReview() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO product_review (id, order_id, product_id, user_id, rating, content, status)
                VALUES (811, 801, 801, 801, 1, '包含不当联系方式', 'VISIBLE')
                """);
        String report = "{\"targetType\":\"REVIEW\",\"targetId\":811,\"reason\":\"疑似发布广告联系方式\"}";
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + otherStudentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + otherStudentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        Long reportId = jdbcTemplate.queryForObject(
                "SELECT id FROM report_record WHERE reporter_id = 802 AND target_type = 'REVIEW' AND target_id = 811",
                Long.class);

        mockMvc.perform(get("/api/v1/admin/reports")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetSummary").value("包含不当联系方式"));

        mockMvc.perform(post("/api/v1/admin/reports/{id}/resolve", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"确认违规，已隐藏评价\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.handleResult").value("确认违规，已隐藏评价"));

        mockMvc.perform(get("/api/v1/products/801/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/api/v1/admin/reports/{id}/resolve", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"重复处理\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
    }
}
