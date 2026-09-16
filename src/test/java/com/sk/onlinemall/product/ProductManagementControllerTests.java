package com.sk.onlinemall.product;

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

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductManagementControllerTests {
    private static final long PRODUCT_ID = 902L;
    private static final long CATEGORY_ID = 91L;
    private static final long USER_ID = 1902L;
    private static final long ACTIVITY_ID = 1904L;
    private static final long ORDER_ID = 1905L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    /**
     * 准备运营商品查询测试数据。
     */
    @BeforeEach
    void setUpProduct() {
        cleanUpProduct();
        jdbcTemplate.update("INSERT INTO product_category (id, name, sort_order, status) VALUES (?, ?, 1, 'ACTIVE')",
                CATEGORY_ID, "Management Test Category");
        jdbcTemplate.update("""
                INSERT INTO product
                  (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (?, ?, 'Draft Management Product', 'NORMAL', 'DRAFT', 29.90, 12, 0, 1)
                """, PRODUCT_ID, CATEGORY_ID);
    }

    /**
     * 清理运营商品查询测试数据。
     */
    @AfterEach
    void tearDownProduct() {
        cleanUpProduct();
    }

    /**
     * 验证学生角色不能读取运营商品目录。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectStudentManagementList() throws Exception {
        String token = tokenService.issue(1L, "student", "STUDENT");
        mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证运营人员可以查询草稿商品。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposeDraftProductsToOperator() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(get("/api/v1/admin/products")
                        .param("keyword", "Draft Management")
                        .param("status", "DRAFT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
    }

    /**
     * 验证仅调整库存时保留商品状态。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldKeepStatusForInventoryOnlyUpdate() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(put("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":91,"pickupPointId":1,"name":"Draft Management Product","subtitle":null,
                                 "description":null,"saleType":"NORMAL","price":29.90,
                                 "stock":36,"limitPerUser":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(36))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    /**
     * 验证内容变化后商品重新进入审核。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldReturnChangedContentToReview() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", PRODUCT_ID);
        mockMvc.perform(put("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":91,"pickupPointId":1,"name":"Updated Management Product","subtitle":"new",
                                 "description":null,"saleType":"NORMAL","price":29.90,
                                 "stock":12,"limitPerUser":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM product WHERE id = ?", String.class, PRODUCT_ID))
                .isEqualTo("Updated Management Product");
    }

    /**
     * 验证运营人员可下架并重新上架未改动的商品。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldTakeProductOffSaleAndPutItBackOnSale() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", PRODUCT_ID);

        mockMvc.perform(post("/api/v1/admin/products/{productId}/off-sale", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));

        mockMvc.perform(get("/api/v1/products/{productId}", PRODUCT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/products/{productId}/on-sale", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    /**
     * 验证商品生命周期接口拒绝非法状态转换。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectInvalidProductLifecycleTransition() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(post("/api/v1/admin/products/{productId}/on-sale", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_OFF_SALE"));
    }

    /**
     * 验证运营人员可删除没有业务历史的非在售商品。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldDeleteUnreferencedNonPublicProduct() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE id = ?", Integer.class, PRODUCT_ID)).isZero();
    }

    /**
     * 验证在售商品不能直接物理删除。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectDeletingOnSaleProduct() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", PRODUCT_ID);

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_ON_SALE"));
    }

    /**
     * 验证已有订单历史的商品不能物理删除。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectDeletingProductWithOrderHistory() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, role, status)
                VALUES (?, 'product-delete-user', 'hash', 'STUDENT', 'ACTIVE')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO trade_order
                  (id, order_no, request_no, user_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount)
                VALUES (?, 'DELETE-ORDER', 'DELETE-REQUEST', ?, 1, '东区图书馆服务台',
                        '图书馆一层东侧服务台', 'COMPLETED', 29.90)
                """, ORDER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO trade_order_item
                  (order_id, product_id, product_name, unit_price, quantity, line_amount)
                VALUES (?, ?, 'Draft Management Product', 29.90, 1, 29.90)
                """, ORDER_ID, PRODUCT_ID);

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ORDERS"));
    }

    /**
     * 验证被活动引用的商品不能物理删除。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectDeletingProductUsedByActivity() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, start_at, end_at,
                   stock, limit_per_user, payment_timeout_minutes)
                VALUES (?, 'Delete Reference Activity', ?, 1, 'FLASH_SALE', 'UNPUBLISHED', 'PENDING', ?, ?, 5, 1, 15)
                """, ACTIVITY_ID, PRODUCT_ID, Timestamp.valueOf(startAt), Timestamp.valueOf(startAt.plusHours(1)));

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVITIES"));
    }

    /**
     * 验证运营人员可维护规格并自动同步商品汇总库存。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldManageSkusAndSyncProductStock() throws Exception {
        String operatorToken = tokenService.issue(2L, "operator", "OPERATOR");
        String studentToken = tokenService.issue(1L, "student", "STUDENT");
        String body = """
                {"skuCode":"BLUE-M","name":"蓝色 / M","price":39.90,
                 "stock":7,"enabled":true,"sortOrder":1}
                """;

        mockMvc.perform(post("/api/v1/admin/products/{productId}/skus", PRODUCT_ID)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/products/{productId}/skus", PRODUCT_ID)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skuCode").value("BLUE-M"));

        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_sku WHERE product_id = ? AND sku_code = 'BLUE-M'", Long.class, PRODUCT_ID);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, PRODUCT_ID))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM product WHERE id = ?", String.class, PRODUCT_ID))
                .isEqualTo("PENDING_REVIEW");

        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", PRODUCT_ID);
        mockMvc.perform(put("/api/v1/admin/products/{productId}/skus/{skuId}", PRODUCT_ID, skuId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuCode":"BLUE-M","name":"蓝色 / M","price":39.90,
                                 "stock":4,"enabled":true,"sortOrder":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(4));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM product WHERE id = ?", String.class, PRODUCT_ID))
                .isEqualTo("ON_SALE");

        mockMvc.perform(get("/api/v1/products/{productId}/skus", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("蓝色 / M"));

        mockMvc.perform(delete("/api/v1/admin/products/{productId}/skus/{skuId}", PRODUCT_ID, skuId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
    }

    /**
     * 删除本测试使用的商品与分类。
     */
    private void cleanUpProduct() {
        jdbcTemplate.update("DELETE FROM trade_order_item WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM trade_order WHERE id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM flash_activity WHERE id = ?", ACTIVITY_ID);
        jdbcTemplate.update("DELETE FROM product_image WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM product_category WHERE id = ?", CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }
}
