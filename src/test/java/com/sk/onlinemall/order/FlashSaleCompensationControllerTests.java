package com.sk.onlinemall.order;

import com.sk.onlinemall.messaging.RabbitTopology;
import com.sk.onlinemall.messaging.FlashSaleOrderMessage;
import com.sk.onlinemall.order.service.FlashSaleInventoryService;
import com.sk.onlinemall.order.service.FlashSaleRequestService;
import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlashSaleCompensationControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenService tokenService;
    @Autowired private FlashSaleRequestService requestService;

    @MockBean private FlashSaleInventoryService inventoryService;
    @MockBean private RabbitTemplate rabbitTemplate;

    private String studentToken;
    private String operatorToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM flash_sale_compensation");
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
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (971, 'comp-student', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (972, 'comp-operator', 'unused', 'OPERATOR', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (971, 'Compensation Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (971, 971, 'Compensation Product', 'FLASH_SALE', 'ON_SALE', 18.00, 20, 0, 5)
                """);
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, start_at, end_at, stock,
                   limit_per_user, payment_timeout_minutes)
                VALUES (971, 'Compensation Activity', 971, 1, 'FLASH_SALE', 'RUNNING', 'APPROVED',
                        DATEADD('MINUTE', -5, CURRENT_TIMESTAMP), DATEADD('HOUR', 1, CURRENT_TIMESTAMP), 10, 5, 15)
                """);
        insertFailedRequest();
        studentToken = tokenService.issue(971L, "comp-student", "STUDENT");
        operatorToken = tokenService.issue(972L, "comp-operator", "OPERATOR");
    }

    /**
     * 验证仅运营人员可查询失败抢购请求。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldListFailedRequestsOnlyForOperators() throws Exception {
        mockMvc.perform(get("/api/v1/admin/flash-sale/failed-requests")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/flash-sale/failed-requests")
                        .param("activityId", "971")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestNo").value("comp-failed"))
                .andExpect(jsonPath("$.data[0].failureCode").value("FLASH_SALE_ORDER_FAILED"));
    }

    /**
     * 验证失败请求补偿会重新预扣、投递并记录审计。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldReserveAndRepublishFailedRequestWithAudit() throws Exception {
        mockMvc.perform(post("/api/v1/admin/flash-sale/failed-requests/comp-failed/retry")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"RabbitMQ recovered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("RabbitMQ recovered"));

        verify(inventoryService).reserve(any(), eq(971L), eq(2), eq(0), eq("comp-failed"));
        verify(rabbitTemplate).convertAndSend(eq(RabbitTopology.ORDER_EXCHANGE),
                eq(RabbitTopology.ORDER_ROUTING_KEY), any(FlashSaleOrderMessage.class));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM flash_sale_request WHERE request_no = 'comp-failed'", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flash_sale_compensation WHERE request_no = 'comp-failed'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * 验证驳回重复补偿。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectDuplicateCompensation() throws Exception {
        String body = "{\"reason\":\"retry after recovery\"}";
        mockMvc.perform(post("/api/v1/admin/flash-sale/failed-requests/comp-failed/retry")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/flash-sale/failed-requests/comp-failed/retry")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FLASH_SALE_COMPENSATION_STATE_CHANGED"));
    }

    /**
     * 验证释放预约并记录发布失败。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldReleaseReservationAndRecordPublishFailure() throws Exception {
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(eq(RabbitTopology.ORDER_EXCHANGE), eq(RabbitTopology.ORDER_ROUTING_KEY),
                        any(FlashSaleOrderMessage.class));

        mockMvc.perform(post("/api/v1/admin/flash-sale/failed-requests/comp-failed/retry")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retry queue after maintenance\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FLASH_SALE_QUEUE_UNAVAILABLE"));

        verify(inventoryService).release(971L, 971L, 2, "comp-failed", "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM flash_sale_request WHERE request_no = 'comp-failed'", String.class))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM flash_sale_compensation WHERE request_no = 'comp-failed'", String.class))
                .isEqualTo("FAILED");
    }

    /**
     * 验证消费失败会写入最终补偿结果。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldPersistConsumerFailureAsFinalCompensationResult() throws Exception {
        mockMvc.perform(post("/api/v1/admin/flash-sale/failed-requests/comp-failed/retry")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retry after consumer recovery\"}"))
                .andExpect(status().isOk());

        requestService.fail(new FlashSaleOrderMessage(
                        "comp-failed", 971L, 971L, 971L, "comp-student", 2, 0),
                "ORDER_CREATE_REJECTED", "order validation rejected the retry");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM flash_sale_compensation WHERE request_no = 'comp-failed'", String.class))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_message FROM flash_sale_compensation WHERE request_no = 'comp-failed'", String.class))
                .isEqualTo("order validation rejected the retry");
    }

    /**
     * 写入用于人工补偿测试的失败抢购请求。
     */
    private void insertFailedRequest() {
        jdbcTemplate.update("""
                INSERT INTO flash_sale_request
                  (request_no, activity_id, product_id, user_id, quantity, status, failure_code, failure_message)
                VALUES ('comp-failed', 971, 971, 971, 2, 'FAILED',
                        'FLASH_SALE_ORDER_FAILED', 'asynchronous order creation failed')
                """);
    }
}
