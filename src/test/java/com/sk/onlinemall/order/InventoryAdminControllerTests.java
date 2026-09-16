package com.sk.onlinemall.order;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.order.service.FlashSaleInventoryService;
import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryAdminControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenService tokenService;

    @MockBean private FlashSaleInventoryService inventoryService;

    private String studentToken;
    private String operatorToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inventory_reconciliation");
        jdbcTemplate.update("DELETE FROM trade_order_item");
        jdbcTemplate.update("DELETE FROM flash_sale_request");
        jdbcTemplate.update("DELETE FROM trade_order");
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM lottery_batch");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (951, 'inventory-student', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (952, 'inventory-operator', 'unused', 'OPERATOR', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (951, 'Inventory Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (951, 951, 'Inventory Product', 'FLASH_SALE', 'ON_SALE', 12.00, 20, 0, 5)
                """);
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, start_at, end_at, stock,
                   limit_per_user, payment_timeout_minutes)
                VALUES (951, 'Inventory Activity', 951, 1, 'FLASH_SALE', 'RUNNING', 'APPROVED',
                        DATEADD('MINUTE', -5, CURRENT_TIMESTAMP), DATEADD('HOUR', 1, CURRENT_TIMESTAMP), 10, 5, 15)
                """);
        jdbcTemplate.update("""
                INSERT INTO flash_sale_request
                  (request_no, activity_id, product_id, user_id, quantity, status)
                VALUES ('inventory-pending', 951, 951, 951, 2, 'PENDING')
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order
                  (id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount, payment_deadline)
                VALUES (951, 'ORD-INVENTORY', 'inventory-order', 951, 951, 1, '东区图书馆服务台',
                        '图书馆一层东侧服务台', 'WAIT_VERIFICATION', 12.00,
                        DATEADD('MINUTE', 15, CURRENT_TIMESTAMP))
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order_item
                  (order_id, product_id, product_name, unit_price, quantity, line_amount)
                VALUES (951, 951, 'Inventory Product', 12.00, 1, 12.00)
                """);
        studentToken = tokenService.issue(951L, "inventory-student", "STUDENT");
        operatorToken = tokenService.issue(952L, "inventory-operator", "OPERATOR");
        when(inventoryService.acquireReconciliationLock(951L)).thenReturn("inventory-lock");
    }

    /**
     * 验证仅运营人员可查看库存差异。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposeInventoryDifferenceOnlyToOperators() throws Exception {
        when(inventoryService.currentStock(951L)).thenReturn(7);

        mockMvc.perform(get("/api/v1/admin/activities/951/inventory")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/activities/951/inventory")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.databaseAvailableStock").value(10))
                .andExpect(jsonPath("$.data.redisAvailableStock").value(7))
                .andExpect(jsonPath("$.data.pendingQuantity").value(2))
                .andExpect(jsonPath("$.data.paidQuantity").value(1))
                .andExpect(jsonPath("$.data.expectedRedisStock").value(8))
                .andExpect(jsonPath("$.data.consistent").value(false));
    }

    /**
     * 验证 Redis 库存重建后会保存审计记录。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRebuildRedisInventoryAndPersistAuditRecord() throws Exception {
        when(inventoryService.currentStock(951L)).thenReturn(7);

        mockMvc.perform(post("/api/v1/admin/activities/951/inventory/reconcile")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"restore cache after Redis restart\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.redisStockBefore").value(7))
                .andExpect(jsonPath("$.data.redisStockAfter").value(8));

        ArgumentCaptor<Map<Long, Integer>> buyers = ArgumentCaptor.forClass(Map.class);
        verify(inventoryService).rebuild(any(), anyInt(), buyers.capture());
        assertThat(buyers.getValue()).containsEntry(951L, 3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_reconciliation WHERE activity_id = 951 AND status = 'SUCCEEDED'",
                Integer.class)).isEqualTo(1);
    }

    /**
     * 验证库存校准失败也会保存审计记录。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldPersistFailedReconciliationAttempt() throws Exception {
        when(inventoryService.currentStock(951L)).thenReturn(null);
        doThrow(new BusinessException("INVENTORY_RECONCILIATION_FAILED", "Redis is unavailable"))
                .when(inventoryService).rebuild(any(), anyInt(), anyMap());

        mockMvc.perform(post("/api/v1/admin/activities/951/inventory/reconcile")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retry cache rebuild\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVENTORY_RECONCILIATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM inventory_reconciliation WHERE activity_id = 951 ORDER BY id DESC LIMIT 1",
                String.class)).isEqualTo("FAILED");
    }
}
