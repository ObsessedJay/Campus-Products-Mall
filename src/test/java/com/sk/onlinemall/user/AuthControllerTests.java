package com.sk.onlinemall.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.onlinemall.security.CaptchaService;
import com.sk.onlinemall.security.EmailVerificationService;
import com.sk.onlinemall.security.RequestGuardService;
import com.sk.onlinemall.security.RefreshTokenService;
import com.sk.onlinemall.storage.ImageStorageService;
import com.sk.onlinemall.storage.UploadedImageResponse;
import com.sk.onlinemall.user.dto.CaptchaChallengeResponse;
import com.sk.onlinemall.user.dto.EmailCodeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @SpyBean
    private RequestGuardService requestGuardService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private ImageStorageService imageStorageService;

    /**
     * 为每个认证测试准备刷新令牌签发结果。
     */
    @BeforeEach
    void setUpRefreshToken() {
        when(refreshTokenService.issue(anyLong()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 604800));
    }

    /**
     * 验证用户注册后可使用签发的 JWT 访问接口。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRegisterAndUseJwtToken() throws Exception {
        String registerBody = """
                {"email":"student001@qq.com","password":"Password123","confirmPassword":"Password123","nickname":"Test Student","emailCode":"123456"}
                """;

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String token = root.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        assertThat(root.path("data").path("email").asText()).isEqualTo("student001@qq.com");
        assertThat(root.path("data").has("username")).isFalse();
        assertThat(root.path("data").has("refreshToken")).isFalse();

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * 验证商家可通过独立入口注册并取得商家身份。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRegisterMerchantFromDedicatedPortal() throws Exception {
        mockMvc.perform(post("/api/v1/auth/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"merchant-self@qq.com","password":"Password123",
                                 "confirmPassword":"Password123","displayName":"校园主理人",
                                 "merchantName":"校园文创社","emailCode":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("merchant-self@qq.com"))
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));

        verify(emailVerificationService)
                .verifyMerchantRegistrationCode("merchant-self@qq.com", "123456");
    }

    /**
     * 验证商家注册可随 multipart 请求上传并持久化商家照片。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRegisterMerchantWithLogoImage() throws Exception {
        when(imageStorageService.upload(any()))
                .thenReturn(new UploadedImageResponse("merchant-logo.png",
                        "/api/v1/files/images/merchant-logo.png", "image/png", 128));
        MockMultipartFile data = new MockMultipartFile("data", "data", "application/json", """
                {"email":"merchant-logo@qq.com","password":"Password123",
                 "confirmPassword":"Password123","displayName":"负责人",
                 "merchantName":"有照片商家","emailCode":"123456"}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile logo = new MockMultipartFile(
                "logo", "logo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/auth/merchants/register")
                        .file(data)
                        .file(logo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));

        verify(imageStorageService).upload(any());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT m.logo_url FROM merchant_profile m
                JOIN sys_user u ON u.id = m.account_id
                WHERE u.email = 'merchant-logo@qq.com' AND u.role = 'MERCHANT'
                """, String.class)).isEqualTo("/api/v1/files/images/merchant-logo.png");
    }

    /**
     * 验证同一邮箱可分别注册并登录学生端和商家端。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldKeepStudentAndMerchantAccountsIndependentForSameEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shared-portals@qq.com","password":"Student123",
                                 "confirmPassword":"Student123","nickname":"共享邮箱学生",
                                 "emailCode":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        mockMvc.perform(post("/api/v1/auth/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shared-portals@qq.com","password":"Merchant123",
                                 "confirmPassword":"Merchant123","displayName":"负责人",
                                 "merchantName":"共享邮箱商店","emailCode":"654321"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shared-portals@qq.com","password":"Student456",
                                 "confirmPassword":"Student456","nickname":"重复学生账号",
                                 "emailCode":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shared-portals@qq.com","password":"Student123",
                                 "captchaId":"shared-student","captchaCode":"ABCD","portalRole":"STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shared-portals@qq.com","password":"Merchant123",
                                 "captchaId":"shared-merchant","captchaCode":"ABCD","portalRole":"MERCHANT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));
    }

    /**
     * 验证商家负责人显示名可以留空。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRegisterMerchantWithEmptyDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/auth/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"merchant-empty-name@qq.com","password":"Password123",
                                 "confirmPassword":"Password123","displayName":"",
                                 "merchantName":"Campus Store","emailCode":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value(""));
    }

    /**
     * 验证商家负责人显示名不能超过六个字符。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectMerchantDisplayNameLongerThanSixCharacters() throws Exception {
        mockMvc.perform(post("/api/v1/auth/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"merchant-long-name@qq.com","password":"Password123",
                                 "confirmPassword":"Password123","displayName":"1234567",
                                 "merchantName":"Campus Store","emailCode":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * 验证账号不能从不匹配的身份入口登录。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectMismatchedLoginPortal() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"portal-student@qq.com","password":"Password123",
                                 "confirmPassword":"Password123","nickname":"入口测试学生",
                                 "emailCode":"123456"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"portal-student@qq.com","password":"Password123",
                                 "captchaId":"captcha-portal","captchaCode":"ABCD","portalRole":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * 验证两次注册密码不一致时拒绝注册且不消费邮箱验证码。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectMismatchedPasswordConfirmationBeforeEmailCodeConsumption() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mismatch@qq.com","password":"Password123",
                                 "confirmPassword":"Password456","nickname":"Mismatch User",
                                 "emailCode":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));

        verify(emailVerificationService, never())
                .verifyRegistrationCode("mismatch@qq.com", "123456");
    }

    /**
     * 验证刷新令牌通过 HttpOnly Cookie 轮换且不会出现在响应体中。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRotateRefreshTokenFromHttpOnlyCookie() throws Exception {
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@qq.com","password":"Password123","confirmPassword":"Password123","nickname":"Refresh User","emailCode":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("campus_refresh_token=refresh-token", "HttpOnly", "SameSite=Strict"))
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(registerResponse).path("data").path("userId").asLong();
        when(refreshTokenService.consume("refresh-old")).thenReturn(userId);
        when(refreshTokenService.issue(userId))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-new", 604800));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("campus_refresh_token", "refresh-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("campus_refresh_token=refresh-new", "HttpOnly", "SameSite=Strict"));

        verify(refreshTokenService).consume("refresh-old");
    }

    /**
     * 验证邮箱验证码可重置密码并撤销该用户全部刷新会话。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldResetPasswordAndRevokeRefreshSessions() throws Exception {
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset@qq.com","password":"Password123","confirmPassword":"Password123","nickname":"Reset User","emailCode":"123456"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(registerResponse).path("data").path("userId").asLong();

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset@qq.com","emailCode":"654321","newPassword":"Changed123",
                                 "portalRole":"STUDENT"}
                                """))
                .andExpect(status().isOk());

        verify(emailVerificationService).verifyPasswordResetCode("reset@qq.com", "STUDENT", "654321");
        verify(refreshTokenService).revokeAll(userId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset@qq.com","password":"Changed123","captchaId":"captcha-reset",
                                 "captchaCode":"ABCD","portalRole":"STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("reset@qq.com"));
    }

    /**
     * 验证健康检查接口可以匿名访问。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposePublicHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk());
    }

    /**
     * 验证登录必须提交有效图形验证码。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireCaptchaForLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student001@qq.com","password":"Password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * 验证图形验证码接口和注册邮件验证码发送流程。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposeCaptchaAndSendRegistrationEmailCode() throws Exception {
        when(captchaService.createChallenge(null))
                .thenReturn(new CaptchaChallengeResponse("captcha-1", "data:image/png;base64,AA==", 300));
        when(emailVerificationService.sendRegistrationCode("student002@qq.com"))
                .thenReturn(new EmailCodeResponse(600, 60));

        mockMvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").value("captcha-1"));

        verify(captchaService).createChallenge(null);

        when(captchaService.createChallenge("captcha-1"))
                .thenReturn(new CaptchaChallengeResponse("captcha-2", "data:image/png;base64,BB==", 300));
        mockMvc.perform(get("/api/v1/auth/captcha")
                        .queryParam("previousCaptchaId", "captcha-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").value("captcha-2"));
        verify(captchaService).createChallenge("captcha-1");

        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student002@qq.com","captchaId":"captcha-1","captchaCode":"ABCD"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(60));

        verify(captchaService).verify("captcha-1", "ABCD");
        verify(emailVerificationService).sendRegistrationCode("student002@qq.com");
    }

    /**
     * 验证账号密码错误会记录登录风险信号。
     *
     * @throws Exception 接口调用失败时抛出
     */
    @Test
    void shouldRecordInvalidCredentialRiskSignal() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@qq.com","password":"Password123","captchaId":"captcha-2",
                                 "captchaCode":"ABCD","portalRole":"STUDENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verify(requestGuardService).recordLoginFailure("127.0.0.1", "missing@qq.com");
    }
}
