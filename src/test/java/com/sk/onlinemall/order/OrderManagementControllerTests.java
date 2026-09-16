package com.sk.onlinemall.order;

import com.sk.onlinemall.security.JwtTokenService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderManagementControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    private String operatorToken;
    private String studentToken;

    /**
     * 准备订单导出测试数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pickup_verification");
        jdbcTemplate.update("DELETE FROM payment_record");
        jdbcTemplate.update("DELETE FROM trade_order_item");
        jdbcTemplate.update("DELETE FROM flash_sale_request");
        jdbcTemplate.update("DELETE FROM trade_order");
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM lottery_batch");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM operator_pickup_point");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("""
                MERGE INTO pickup_point (id, name, campus, address, status)
                KEY (id) VALUES (1201, '航空港校区提货点', '航空港校区', '学生活动中心 101', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, nickname, password_hash, default_pickup_point_id, role, status)
                VALUES (1201, 'export-student', 'export-student@example.com', '导出学生', 'unused', 1201, 'STUDENT', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, nickname, password_hash, role, status)
                VALUES (1202, 'export-operator', '核销运营员', 'unused', 'OPERATOR', 'ACTIVE')
                """);
        jdbcTemplate.update("INSERT INTO operator_pickup_point (operator_id, pickup_point_id) VALUES (1202, 1201)");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (1201, '导出分类', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (1201, 1201, '银杏校徽', 'FLASH_SALE', 'ON_SALE', ?, 20, 0, 2)
                """, new BigDecimal("29.90"));
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, created_by, mode, status, review_status,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes)
                VALUES (1201, '银杏季限定活动', 1201, 1201, 1202, 'FLASH_SALE', 'RUNNING', 'APPROVED',
                        CURRENT_TIMESTAMP, DATEADD('HOUR', 2, CURRENT_TIMESTAMP), 10, 2, 15)
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order
                  (id, order_no, request_no, user_id, activity_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount, payment_deadline)
                VALUES (1201, 'ORD-EXPORT-VERIFIED', 'REQ-EXPORT-VERIFIED', 1201, 1201, 1201,
                        '航空港校区提货点', '学生活动中心 101', 'COMPLETED', 29.90, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order_item
                  (id, order_id, product_id, product_name, sku_code, sku_name, unit_price, quantity, line_amount)
                VALUES (1201, 1201, 1201, '银杏校徽', 'BADGE-GINKGO', '金色', 29.90, 1, 29.90)
                """);
        jdbcTemplate.update("""
                INSERT INTO pickup_verification
                  (id, order_id, pickup_code, verified_by, verified_at)
                VALUES (1201, 1201, '120001', 1202, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order
                  (id, order_no, request_no, user_id, pickup_point_id, pickup_point_name,
                   pickup_point_address, status, total_amount, payment_deadline)
                VALUES (1202, 'ORD-EXPORT-PENDING', 'REQ-EXPORT-PENDING', 1201, 1201,
                        '航空港校区提货点', '学生活动中心 101', 'WAIT_VERIFICATION', 59.80, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO trade_order_item
                  (id, order_id, product_id, product_name, unit_price, quantity, line_amount)
                VALUES (1202, 1202, 1201, '银杏校徽', 29.90, 2, 59.80)
                """);
        jdbcTemplate.update("""
                INSERT INTO pickup_verification (id, order_id, pickup_code)
                VALUES (1202, 1202, '120002')
                """);
        operatorToken = tokenService.issue(1202L, "export-operator", "OPERATOR");
        studentToken = tokenService.issue(1201L, "export-student", "STUDENT");
    }

    /**
     * 验证运营人员可以筛选并导出已核销活动订单。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExportFilteredVerifiedOrders() throws Exception {
        byte[] content = mockMvc.perform(get("/api/v1/admin/orders/export")
                        .param("activityId", "1201")
                        .param("status", "COMPLETED")
                        .param("pickupPointId", "1201")
                        .param("verificationStatus", "VERIFIED")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"orders-and-verifications.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("订单与核销结果");
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("ORD-EXPORT-VERIFIED", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("导出学生", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("已核销", sheet.getRow(3).getCell(15).getStringCellValue());
            assertEquals("核销运营员", sheet.getRow(3).getCell(17).getStringCellValue());
            assertEquals("金色", sheet.getRow(3).getCell(9).getStringCellValue());
        }
    }

    /**
     * 验证未核销筛选不会混入已核销订单。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExportOnlyPendingVerifications() throws Exception {
        byte[] content = mockMvc.perform(get("/api/v1/admin/orders/export")
                        .param("verificationStatus", "PENDING")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("ORD-EXPORT-PENDING", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("未核销", sheet.getRow(3).getCell(15).getStringCellValue());
        }
    }

    /**
     * 验证学生不能访问运营订单导出接口。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectStudentExportAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/export")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证运营人员不能导出未授权自提点的订单。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExcludeUnauthorizedPickupPoints() throws Exception {
        jdbcTemplate.update("DELETE FROM operator_pickup_point WHERE operator_id = 1202");
        byte[] content = mockMvc.perform(get("/api/v1/admin/orders/export")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertEquals(2, workbook.getSheetAt(0).getLastRowNum());
        }
    }

    /**
     * 验证非法导出状态返回明确业务错误。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectInvalidExportStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/export")
                        .param("status", "UNKNOWN")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    }
}
