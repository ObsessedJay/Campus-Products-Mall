package com.sk.onlinemall.user;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    private String studentToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 801");
        jdbcTemplate.update("DELETE FROM pickup_point WHERE id IN (801, 802)");
        jdbcTemplate.update("""
                INSERT INTO pickup_point (id, name, campus, address, longitude, latitude, opening_hours, status)
                VALUES (801, 'North Library', 'North Campus', 'Library Room 101', 116.3103160, 39.9922630, '09:00-18:00', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO pickup_point (id, name, campus, address, opening_hours, status)
                VALUES (802, 'Closed Station', 'South Campus', 'Building 2', 'CLOSED', 'INACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, nickname, school, student_no, role, status)
                VALUES (801, 'profile-student', 'unused', 'Original Name', 'Original School', '202600801', 'STUDENT', 'ACTIVE')
                """);
        studentToken = tokenService.issue(801L, "profile-student", "STUDENT");
    }

    /**
     * 验证用户可更新资料且响应不再包含默认领取点。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldUpdateProfileWithoutPickupPoint() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"Updated Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Updated Name"))
                .andExpect(jsonPath("$.data.school").value("成都信息工程大学"))
                .andExpect(jsonPath("$.data.studentNo").doesNotExist())
                .andExpect(jsonPath("$.data.defaultPickupPoint").doesNotExist());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Updated Name"));
    }

    /**
     * 验证公开接口仅返回启用的自提点。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldListOnlyActivePickupPoints() throws Exception {
        mockMvc.perform(get("/api/v1/pickup-points")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 801)]").exists())
                .andExpect(jsonPath("$.data[?(@.id == 801)].longitude").value(116.310316))
                .andExpect(jsonPath("$.data[?(@.id == 801)].latitude").value(39.992263))
                .andExpect(jsonPath("$.data[?(@.id == 802)]").doesNotExist());
    }

    /**
     * 验证资料接口需要登录。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldProtectProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
