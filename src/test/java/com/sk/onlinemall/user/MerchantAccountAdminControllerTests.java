package com.sk.onlinemall.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.onlinemall.security.CaptchaService;
import com.sk.onlinemall.security.JwtTokenService;
import com.sk.onlinemall.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantAccountAdminControllerTests {
    private static final long ADMIN_ID = 9910L;
    private static final long STUDENT_ID = 9911L;
    private static final String MERCHANT_EMAIL = "merchant-admin-test@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    private String adminToken;
    private String studentToken;

    /**
     * 准备管理员、学生和刷新令牌测试数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                DELETE FROM account_operation_log
                WHERE operator_account_id IN (?, ?)
                   OR target_account_id IN (SELECT id FROM sys_user WHERE email = ?)
                """, ADMIN_ID, STUDENT_ID, MERCHANT_EMAIL);
        jdbcTemplate.update("""
                DELETE FROM merchant_profile
                WHERE account_id IN (
                    SELECT id FROM sys_user
                    WHERE email = ? OR (email = 'merchant-student@example.com' AND role = 'MERCHANT')
                )
                """, MERCHANT_EMAIL);
        jdbcTemplate.update("""
                DELETE FROM sys_user
                WHERE email = ? OR id IN (?, ?)
                   OR (email = 'merchant-student@example.com' AND role = 'MERCHANT')
                """, MERCHANT_EMAIL, ADMIN_ID, STUDENT_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, role, status)
                VALUES (?, 'merchant-account-admin', 'merchant-admin@example.com', 'unused',
                        'Account Admin', 'ADMIN', 'ACTIVE')
                """, ADMIN_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, email, password_hash, nickname, role, status)
                VALUES (?, 'merchant-account-student', 'merchant-student@example.com', 'unused',
                        'Student', 'STUDENT', 'ACTIVE')
                """, STUDENT_ID);
        adminToken = tokenService.issue(ADMIN_ID, "merchant-account-admin", "ADMIN");
        studentToken = tokenService.issue(STUDENT_ID, "merchant-account-student", "STUDENT");
        when(refreshTokenService.issue(anyLong()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("merchant-refresh-token", 604800));
    }

    /**
     * 验证管理员可原子创建商家账号、资料和审计记录，并可用新账号登录商家端。
     *
     * @throws Exception 请求执行异常时抛出
     */
    @Test
    void shouldCreateMerchantAccountAndAllowMerchantLogin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/merchants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(MERCHANT_EMAIL))
                .andExpect(jsonPath("$.data.role").value("MERCHANT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.merchantName").value("Campus Culture Store"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE email = ?", String.class, MERCHANT_EMAIL);
        assertThat(passwordEncoder.matches("Merchant123", storedHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_profile WHERE merchant_name = ?", Integer.class,
                "Campus Culture Store")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_operation_log WHERE action = ?", Integer.class,
                "CREATE_MERCHANT_ACCOUNT")).isEqualTo(1);

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"merchant-admin-test@example.com","password":"Merchant123",
                                 "captchaId":"merchant-captcha","captchaCode":"ABCD","portalRole":"MERCHANT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode loginData = objectMapper.readTree(loginResponse).path("data");

        mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + loginData.path("token").asText()))
                .andExpect(status().isOk());
    }

    /**
     * 验证初始密码确认不一致时不创建任何账号数据。
     *
     * @throws Exception 请求执行异常时抛出
     */
    @Test
    void shouldRejectMismatchedMerchantPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/merchants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"merchant-admin-test@example.com","password":"Merchant123",
                                 "confirmPassword":"Merchant456","displayName":"Campus Operator",
                                 "merchantName":"Campus Culture Store"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = ?", Integer.class, MERCHANT_EMAIL)).isZero();
    }

    /**
     * 验证学生邮箱可以独立创建商家账号。
     *
     * @throws Exception 请求执行异常时抛出
     */
    @Test
    void shouldAllowStudentEmailForMerchantAccount() throws Exception {
        String request = validRequest().replace(MERCHANT_EMAIL, "merchant-student@example.com");

        mockMvc.perform(post("/api/v1/admin/accounts/merchants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("merchant-student@example.com"))
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = ?", Integer.class,
                "merchant-student@example.com")).isEqualTo(2);
    }

    /**
     * 验证学生账号不能创建商家账号。
     *
     * @throws Exception 请求执行异常时抛出
     */
    @Test
    void shouldForbidStudentFromCreatingMerchantAccount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/merchants")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    /**
     * 构造合法商家账号创建请求。
     *
     * @return JSON 请求体
     */
    private String validRequest() {
        return """
                {"email":"merchant-admin-test@example.com","password":"Merchant123",
                 "confirmPassword":"Merchant123","displayName":"Campus Operator",
                 "merchantName":"Campus Culture Store","contactName":"Li Ming",
                 "contactPhone":"13800000000"}
                """;
    }
}
