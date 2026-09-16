package com.sk.onlinemall.activity;

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
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.sk.onlinemall.notification.model.NotificationType;
import com.sk.onlinemall.notification.service.NotificationPublisher;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class FlashActivityControllerTests {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    @MockBean
    private NotificationPublisher notificationPublisher;

    private String operatorToken;
    private String studentToken;
    private String secondStudentToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM activity_reservation");
        jdbcTemplate.update("DELETE FROM lottery_batch");
        jdbcTemplate.update("DELETE FROM flash_activity");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (601, 'activity-student', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (602, 'activity-operator', 'unused', 'OPERATOR', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (603, 'activity-student-2', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("UPDATE sys_user SET email = 'activity-student@example.com', nickname = 'Student One' WHERE id = 601");
        jdbcTemplate.update("UPDATE sys_user SET email = 'activity-student-2@example.com', nickname = 'Student Two' WHERE id = 603");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (601, 'Activity Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (601, 601, 'Activity Product', 'FLASH_SALE', 'ON_SALE', ?, 20, 0, 1)
                """, new BigDecimal("19.90"));
        operatorToken = tokenService.issue(602L, "activity-operator", "OPERATOR");
        studentToken = tokenService.issue(601L, "activity-student", "STUDENT");
        secondStudentToken = tokenService.issue(603L, "activity-student-2", "STUDENT");
    }

    /**
     * 验证创建发布并预约或预扣活动幂等性。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCreatePublishAndReserveActivityIdempotently() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String reservationStart = now.minusMinutes(1).format(ISO);
        String reservationEnd = now.plusHours(2).format(ISO);
        String start = now.plusHours(3).format(ISO);
        String end = now.plusHours(5).format(ISO);
        String body = """
                {
                  "name":"Freshman Flash Sale",
                  "productId":601,
                  "mode":"LOTTERY",
                  "reservationStartAt":"%s",
                  "reservationEndAt":"%s",
                  "startAt":"%s",
                  "endAt":"%s",
                  "stock":10,
                  "limitPerUser":1,
                  "paymentTimeoutMinutes":15,
                  "ruleDescription":"One reservation per student"
                }
                """.formatted(reservationStart, reservationEnd, start, end);

        String createResponse = mockMvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        long activityId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/activities/{id}/publish", activityId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_APPROVED"));

        jdbcTemplate.update("UPDATE flash_activity SET review_status = 'APPROVED' WHERE id = ?", activityId);
        mockMvc.perform(post("/api/v1/activities/{id}/publish", activityId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVING"));

        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(activityId));

        String first = mockMvc.perform(post("/api/v1/activities/{id}/reservations", activityId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reservationNo", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String firstNo = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(first).path("data").path("reservationNo").asText();

        String second = mockMvc.perform(post("/api/v1/activities/{id}/reservations", activityId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondNo = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(second).path("data").path("reservationNo").asText();
        assertEquals(firstNo, secondNo);
        verify(notificationPublisher, times(1)).publishAfterCommit(argThat(message ->
                message.type() == NotificationType.ACTIVITY_REMINDER
                        && message.userId().equals(601L)
                        && message.businessKey().equals("activity-reminder:" + activityId + ":601")));

        mockMvc.perform(get("/api/v1/activities/{id}/reservation", activityId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationNo").value(firstNo));

        mockMvc.perform(get("/api/v1/activities/{id}/reservation", activityId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/activities/{id}/terminate", activityId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"campaign cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TERMINATED"))
                .andExpect(jsonPath("$.data.terminateReason").value("campaign cancelled"));
    }

    /**
     * 验证运营人员可查询和编辑未发布活动且编辑后重新送审。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldListAndUpdateUnpublishedActivityForManagement() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, reservation_start_at,
                   reservation_end_at, start_at, end_at, stock, limit_per_user, payment_timeout_minutes)
                VALUES (620, 'Editable Activity', 601, 1, 'FLASH_SALE', 'UNPUBLISHED', 'APPROVED', ?, ?, ?, ?, 5, 1, 15)
                """, now.plusMinutes(5), now.plusHours(1), now.plusHours(2), now.plusHours(3));

        mockMvc.perform(get("/api/v1/admin/activities")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/activities")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(620));

        String body = """
                {
                  "name":"Edited Activity",
                  "productId":601,
                  "mode":"PRE_SALE",
                  "startAt":"%s",
                  "endAt":"%s",
                  "stock":8,
                  "limitPerUser":2,
                  "paymentTimeoutMinutes":20,
                  "ruleDescription":"Edited rules"
                }
                """.formatted(now.plusHours(4).format(ISO), now.plusHours(6).format(ISO));

        mockMvc.perform(put("/api/v1/admin/activities/620")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Edited Activity"))
                .andExpect(jsonPath("$.data.mode").value("PRE_SALE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"));
    }

    /**
     * 验证学生无权创建抢购活动。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectActivityCreationForStudent() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证活动不能继承商品的停用领取点。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectInactiveProductPickupPointForActivity() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO pickup_point (id, name, campus, address, status)
                KEY (id) VALUES (699, '停用测试点', '测试校区', '封闭区域', 'INACTIVE')
                """);
        jdbcTemplate.update("UPDATE product SET pickup_point_id = 699 WHERE id = 601");
        LocalDateTime now = LocalDateTime.now();
        String body = """
                {
                  "name":"Invalid Pickup Activity",
                  "productId":601,
                  "mode":"FLASH_SALE",
                  "startAt":"%s",
                  "endAt":"%s",
                  "stock":1,
                  "limitPerUser":1,
                  "paymentTimeoutMinutes":15
                }
                """.formatted(now.plusHours(1).format(ISO), now.plusHours(2).format(ISO));

        mockMvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PICKUP_POINT_NOT_AVAILABLE"));
    }

    /**
     * 验证抽签仅执行一次且结果可追溯查询。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRunTraceableLotteryOnceAndPublishQualificationResults() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status, reservation_start_at,
                   reservation_end_at, start_at, end_at, stock, limit_per_user, payment_timeout_minutes)
                VALUES (610, 'Traceable Lottery', 601, 1, 'LOTTERY', 'RESERVING', 'APPROVED', ?, ?, ?, ?, 1, 1, 15)
                """, now.minusHours(2), now.minusMinutes(1), now.plusHours(1), now.plusHours(2));
        jdbcTemplate.update("""
                INSERT INTO activity_reservation (id, activity_id, user_id, reservation_no, status)
                VALUES (610, 610, 601, 'RSV-610-A', 'PENDING')
                """);
        jdbcTemplate.update("""
                INSERT INTO activity_reservation (id, activity_id, user_id, reservation_no, status)
                VALUES (611, 610, 603, 'RSV-610-B', 'PENDING')
                """);

        String first = mockMvc.perform(post("/api/v1/activities/610/lottery")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":20260821,\"winnerCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.randomSeed").value(20260821))
                .andExpect(jsonPath("$.data.batch.totalReservations").value(2))
                .andExpect(jsonPath("$.data.batch.winnerCount").value(1))
                .andExpect(jsonPath("$.data.reservations", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String batchNo = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(first).path("data").path("batch").path("batchNo").asText();

        mockMvc.perform(post("/api/v1/activities/610/lottery")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":99,\"winnerCount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.batchNo").value(batchNo))
                .andExpect(jsonPath("$.data.batch.randomSeed").value(20260821));

        Integer qualified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_reservation WHERE activity_id = 610 AND status = 'QUALIFIED'",
                Integer.class);
        Integer rejected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_reservation WHERE activity_id = 610 AND status = 'NOT_QUALIFIED'",
                Integer.class);
        assertEquals(1, qualified);
        assertEquals(1, rejected);

        mockMvc.perform(get("/api/v1/activities/610/reservation")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotteryBatchId", notNullValue()))
                .andExpect(jsonPath("$.data.drawRank", notNullValue()));

        mockMvc.perform(post("/api/v1/activities/610/lottery")
                        .header("Authorization", "Bearer " + secondStudentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证运营人员可筛选预约名单并导出结构正确的 XLSX 文件。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldListAndExportReservationRosterForOperator() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO flash_activity
                  (id, name, product_id, pickup_point_id, mode, status, review_status,
                   start_at, end_at, stock, limit_per_user, payment_timeout_minutes)
                VALUES (620, 'Roster Export', 601, 1, 'LOTTERY', 'PENDING', 'APPROVED', ?, ?, 2, 1, 15)
                """, now.plusHours(1), now.plusHours(2));
        jdbcTemplate.update("""
                INSERT INTO lottery_batch
                  (id, activity_id, batch_no, random_seed, total_reservations, winner_count, drawn_at)
                VALUES (620, 620, 'LOT-ROSTER-620', 20260824, 2, 1, ?)
                """, now);
        jdbcTemplate.update("""
                INSERT INTO activity_reservation
                  (id, activity_id, user_id, reservation_no, status, lottery_batch_id, draw_rank)
                VALUES (620, 620, 601, 'RSV-ROSTER-A', 'QUALIFIED', 620, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO activity_reservation
                  (id, activity_id, user_id, reservation_no, status, lottery_batch_id, draw_rank)
                VALUES (621, 620, 603, 'RSV-ROSTER-B', 'NOT_QUALIFIED', 620, 2)
                """);

        mockMvc.perform(get("/api/v1/admin/activities/620/reservations")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("status", "QUALIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("activity-student@example.com"))
                .andExpect(jsonPath("$.data[0].nickname").value("Student One"))
                .andExpect(jsonPath("$.data[0].lotteryBatchNo").value("LOT-ROSTER-620"))
                .andExpect(jsonPath("$.data[0].drawRank").value(1));

        byte[] workbookBytes = mockMvc.perform(get("/api/v1/admin/activities/620/reservations/export")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        result.getResponse().getContentType()))
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("预约名单");
            assertEquals("Roster Export - 预约名单", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("预约编号", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("RSV-ROSTER-A", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("activity-student@example.com", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("RSV-ROSTER-B", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals(3, sheet.getPaneInformation().getHorizontalSplitPosition());
        }

        mockMvc.perform(get("/api/v1/admin/activities/620/reservations")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/activities/620/reservations")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATUS"));
    }
}
