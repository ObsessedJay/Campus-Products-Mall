package com.sk.onlinemall.order;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sk.onlinemall.order.service.TradeOrderService;
import com.sk.onlinemall.order.service.FlashSaleInventoryService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.sk.onlinemall.notification.model.NotificationType;
import com.sk.onlinemall.notification.service.NotificationPublisher;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeOrderControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeOrderService orderService;

    @MockBean
    private FlashSaleInventoryService flashSaleInventoryService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private NotificationPublisher notificationPublisher;

    private String studentToken;
    private String operatorToken;
    private String adminToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM operator_pickup_point");
        jdbcTemplate.update("DELETE FROM refund_record");
        jdbcTemplate.update("DELETE FROM pickup_verification");
        jdbcTemplate.update("DELETE FROM payment_record");
        jdbcTemplate.update("DELETE FROM trade_order_item");
        jdbcTemplate.update("DELETE FROM flash_sale_request");
        jdbcTemplate.update("DELETE FROM trade_order");
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM lottery_batch");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("""
                MERGE INTO pickup_point (id, name, campus, address, status)
                KEY (id) VALUES (801, '订单测试自提点', '测试校区', '测试楼 101 室', 'ACTIVE')
                """);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (801, 'order-student', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (802, 'order-operator', 'unused', 'OPERATOR', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (803, 'order-admin', 'unused', 'ADMIN', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (801, 'Order Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, pickup_point_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (801, 801, 801, 'Order Product', 'NORMAL', 'ON_SALE', 10.00, 5, 0, 2)
                """);
        studentToken = tokenService.issue(801L, "order-student", "STUDENT");
        operatorToken = tokenService.issue(802L, "order-operator", "OPERATOR");
        adminToken = tokenService.issue(803L, "order-admin", "ADMIN");
    }

    /**
     * 验证创建并支付订单幂等性。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCreateAndPayOrderIdempotently() throws Exception {
        String createBody = "{\"requestNo\":\"req-801\",\"productId\":801,\"quantity\":2}";
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAIT_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(20.0))
                .andExpect(jsonPath("$.data.pickupPointId").value(801))
                .andExpect(jsonPath("$.data.pickupPointName").value("订单测试自提点"))
                .andExpect(jsonPath("$.data.pickupPointAddress").value("测试楼 101 室"))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        String paymentBody = "{\"idempotencyKey\":\"pay-801\"}";
        String payment = mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        String paymentNo = objectMapper.readTree(payment).path("data").path("paymentNo").asText();

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").value(paymentNo));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("WAIT_VERIFICATION"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.payment.paymentNo").value(paymentNo))
                .andExpect(jsonPath("$.data.pickupVerification.pickupCode").isNotEmpty());
        verify(notificationPublisher, times(1)).publishAfterCommit(argThat(message ->
                message.type() == NotificationType.PAYMENT_SUCCESS
                        && message.userId().equals(801L)
                        && message.businessKey().equals("payment:" + paymentNo)));
    }

    /**
     * 验证规格价格快照以及取消订单后的双层库存回补。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldUseSkuPriceAndRestoreSkuStockOnCancel() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO product_sku (id, product_id, sku_code, name, price, stock, enabled, sort_order)
                VALUES (811, 801, 'NAVY-M', '海军蓝 / M', 12.50, 3, TRUE, 0)
                """);
        jdbcTemplate.update("UPDATE product SET stock = 3 WHERE id = 801");

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-sku\",\"productId\":801,\"skuId\":811,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(25.0))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].skuId").value(811))
                .andExpect(jsonPath("$.data.items[0].skuCode").value("NAVY-M"))
                .andExpect(jsonPath("$.data.items[0].skuName").value("海军蓝 / M"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(12.5));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM product_sku WHERE id = 811", Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = 801", Integer.class)).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/admin/products/{productId}/skus/{skuId}", 801, 811)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SKU_IN_USE"));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM product_sku WHERE id = 811", Integer.class)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = 801", Integer.class)).isEqualTo(3);
    }

    /**
     * 验证存在规格时必须选择所属且库存充足的规格。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireAvailableOwnedSku() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO product_sku (id, product_id, sku_code, name, price, stock, enabled, sort_order)
                VALUES (812, 801, 'LIMITED', '限量规格', 18.00, 1, TRUE, 0)
                """);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-sku-missing\",\"productId\":801,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SKU_REQUIRED"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-sku-stock\",\"productId\":801,\"skuId\":812,\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SKU_STOCK_INSUFFICIENT"));
    }

    /**
     * 验证支付后生成提货码且仅可核销一次。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldGeneratePickupCodeAndAllowOperatorToVerifyOnce() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-pickup\",\"productId\":801,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"pay-pickup\"}"))
                .andExpect(status().isOk());

        String pickupResponse = mockMvc.perform(get("/api/v1/orders/{id}/pickup", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verifiedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String pickupCode = objectMapper.readTree(pickupResponse).path("data").path("pickupCode").asText();

        mockMvc.perform(post("/api/v1/orders/verification")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"" + pickupCode + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders/verification")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"" + pickupCode + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PICKUP_POINT_FORBIDDEN"));

        jdbcTemplate.update("INSERT INTO operator_pickup_point (operator_id, pickup_point_id) VALUES (802, 801)");

        mockMvc.perform(post("/api/v1/orders/verification")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"" + pickupCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/orders/verification")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"" + pickupCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_VERIFIED"));
    }

    /**
     * 验证取消待支付订单会恢复库存。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCancelUnpaidOrderAndRestoreStock() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-cancel\",\"productId\":801,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = 801", Integer.class);
        org.assertj.core.api.Assertions.assertThat(stock).isEqualTo(5);
    }

    /**
     * 验证超时订单关闭和库存恢复具备幂等性。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCloseExpiredUnpaidOrdersAndRestoreStockIdempotently() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-expired\",\"productId\":801,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();
        jdbcTemplate.update("UPDATE trade_order SET payment_deadline = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id = ?", orderId);

        org.assertj.core.api.Assertions.assertThat(orderService.closeExpiredOrders(java.time.LocalDateTime.now())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(orderService.closeExpiredOrders(java.time.LocalDateTime.now())).isZero();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("CANCELLED"));
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = 801", Integer.class);
        org.assertj.core.api.Assertions.assertThat(stock).isEqualTo(5);
    }

    /**
     * 验证退款申请幂等且运营人员可以审批。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequestRefundIdempotentlyAndAllowOperatorToApprove() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-refund\",\"productId\":801,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"pay-refund\"}"))
                .andExpect(status().isOk());

        String refundResponse = mockMvc.perform(post("/api/v1/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String refundNo = objectMapper.readTree(refundResponse).path("data").path("refundNo").asText();

        mockMvc.perform(post("/api/v1/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundNo").value(refundNo));

        mockMvc.perform(get("/api/v1/orders/{id}/pickup", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PICKUP_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/v1/orders/{id}/refund/approve", orderId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"approved\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders/{id}/refund/approve", orderId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"approved by operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refund.refundNo").value(refundNo));
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = 801", Integer.class);
        org.assertj.core.api.Assertions.assertThat(stock).isEqualTo(5);

        mockMvc.perform(post("/api/v1/orders/{id}/refund/approve", orderId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate approval\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFUND_ALREADY_PROCESSED"));
    }

    /**
     * 验证匿名用户不能访问订单接口。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldProtectOrdersFromAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证普通订单要求商品领取点保持可用。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireActiveProductPickupPointForNormalOrder() throws Exception {
        jdbcTemplate.update("UPDATE pickup_point SET status = 'INACTIVE' WHERE id = 801");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-no-pickup\",\"productId\":801,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PICKUP_POINT_NOT_AVAILABLE"));
    }

    /**
     * 验证只有管理员可以维护运营人员的自提点授权。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldAllowOnlyAdminToManageOperatorPickupPoints() throws Exception {
        mockMvc.perform(put("/api/v1/admin/operators/802/pickup-points")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupPointIds\":[801]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/operators/802/pickup-points")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupPointIds\":[801,801]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(801));

        mockMvc.perform(get("/api/v1/admin/operators/802/pickup-points")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("订单测试自提点"));
    }

    /**
     * 验证抽签资格和活动限购规则均会生效。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireLotteryQualificationAndEnforceActivityLimit() throws Exception {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, start_at, end_at, stock,
                   limit_per_user, payment_timeout_minutes)
                VALUES (810, 'Qualified Sale', 801, 1, 'LOTTERY', 'RUNNING', 'APPROVED', ?, ?, 2, 1, 8)
                """, now.minusMinutes(1), now.plusHours(1));
        jdbcTemplate.update("""
                INSERT INTO activity_reservation (id, activity_id, user_id, reservation_no, status)
                VALUES (810, 810, 801, 'RSV-ORDER-810', 'PENDING')
                """);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-unqualified\",\"productId\":801,\"quantity\":1,\"activityId\":810}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_QUALIFICATION_REQUIRED"));

        jdbcTemplate.update("UPDATE activity_reservation SET status = 'QUALIFIED' WHERE id = 810");
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-qualified\",\"productId\":801,\"quantity\":1,\"activityId\":810}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activityId").value(810))
                .andExpect(jsonPath("$.data.pickupPointId").value(801))
                .andExpect(jsonPath("$.data.pickupPointName").value("订单测试自提点"))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"req-over-limit\",\"productId\":801,\"quantity\":1,\"activityId\":810}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_LIMIT_EXCEEDED"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM flash_activity WHERE id = 810", Integer.class)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM flash_activity WHERE id = 810", Integer.class)).isEqualTo(2);
    }

    /**
     * 验证抢购请求受理幂等且结果可查询。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldAcceptFlashSaleRequestIdempotentlyAndExposeResult() throws Exception {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, start_at, end_at, stock,
                   limit_per_user, payment_timeout_minutes)
                VALUES (820, 'Queued Sale', 801, 801, 'FLASH_SALE', 'RUNNING', 'APPROVED', ?, ?, 3, 1, 8)
                """, now.minusMinutes(1), now.plusHours(1));
        String body = "{\"requestNo\":\"req-queued\",\"productId\":801,\"quantity\":1,\"activityId\":820}";

        mockMvc.perform(post("/api/v1/orders/flash-sale")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.requestNo").value("req-queued"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/orders/flash-sale")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.requestNo").value("req-queued"));

        mockMvc.perform(get("/api/v1/orders/flash-sale/{requestNo}", "req-queued")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flash_sale_request WHERE request_no = 'req-queued'", Integer.class))
                .isEqualTo(1);
    }
}
