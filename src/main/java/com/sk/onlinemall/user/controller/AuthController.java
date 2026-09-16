package com.sk.onlinemall.user.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.security.CaptchaService;
import com.sk.onlinemall.security.EmailVerificationService;
import com.sk.onlinemall.security.RequestGuardService;
import com.sk.onlinemall.user.dto.AuthTokenResponse;
import com.sk.onlinemall.user.dto.CaptchaChallengeResponse;
import com.sk.onlinemall.user.dto.EmailCodeResponse;
import com.sk.onlinemall.user.dto.LoginRequest;
import com.sk.onlinemall.user.dto.RegisterRequest;
import com.sk.onlinemall.user.dto.ResetPasswordRequest;
import com.sk.onlinemall.user.dto.SendEmailCodeRequest;
import com.sk.onlinemall.user.dto.SendPasswordResetEmailCodeRequest;
import com.sk.onlinemall.user.dto.MerchantRegisterRequest;
import com.sk.onlinemall.user.model.AuthSession;
import com.sk.onlinemall.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "campus_refresh_token";
    private final UserService userService;
    private final CaptchaService captchaService;
    private final EmailVerificationService emailVerificationService;
    private final RequestGuardService requestGuardService;
    private final boolean refreshCookieSecure;

    /**
     * 创建认证控制器。
     *
     * @param userService 用户认证服务
     * @param captchaService 图形验证码服务
     * @param emailVerificationService 邮箱验证码服务
     * @param requestGuardService 请求风控服务
     * @param refreshCookieSecure 是否仅通过 HTTPS 发送刷新 Cookie
     */
    public AuthController(UserService userService, CaptchaService captchaService,
                          EmailVerificationService emailVerificationService,
                          RequestGuardService requestGuardService,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${app.security.refresh-cookie-secure:false}") boolean refreshCookieSecure) {
        this.userService = userService;
        this.captchaService = captchaService;
        this.emailVerificationService = emailVerificationService;
        this.requestGuardService = requestGuardService;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    /**
     * 创建图形验证码挑战并销毁被替换的旧挑战。
     *
     * @param previousCaptchaId 被替换的验证码标识，可为空
     * @return 图形验证码标识、图片和有效期
     */
    @GetMapping("/captcha")
    public ApiResponse<CaptchaChallengeResponse> captcha(
            @RequestParam(required = false) String previousCaptchaId) {
        return ApiResponse.success(captchaService.createChallenge(previousCaptchaId));
    }

    /**
     * 校验图形验证码并发送注册邮箱验证码。
     *
     * @param request 邮箱验证码请求
     * @return 已受理的邮箱验证码回执
     */
    @PostMapping("/email-codes")
    public ResponseEntity<ApiResponse<EmailCodeResponse>> sendEmailCode(
            @Valid @RequestBody SendEmailCodeRequest request) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        EmailCodeResponse response = emailVerificationService.sendRegistrationCode(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /**
     * 注册学生账号并签发 JWT。
     *
     * @param request 注册请求
     * @return 新账号认证信息
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return sessionResponse(userService.register(request), HttpStatus.CREATED);
    }

    /**
     * 校验图形验证码并发送商家注册邮箱验证码。
     *
     * @param request 邮箱验证码请求
     * @return 已受理的验证码回执
     */
    @PostMapping("/merchants/email-codes")
    public ResponseEntity<ApiResponse<EmailCodeResponse>> sendMerchantEmailCode(
            @Valid @RequestBody SendEmailCodeRequest request) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        EmailCodeResponse response = emailVerificationService.sendMerchantRegistrationCode(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /**
     * 注册商家账号并签发 JWT。
     *
     * @param request 商家注册请求
     * @return 新商家认证信息
     */
    @PostMapping(value = "/merchants/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> registerMerchant(
            @Valid @RequestBody MerchantRegisterRequest request) {
        return sessionResponse(userService.registerMerchant(request), HttpStatus.CREATED);
    }

    /**
     * 注册带商家头像的账号并签发 JWT。
     *
     * @param request 商家注册 JSON 数据
     * @param logo 可选商家头像或门店标识图
     * @return 新商家认证信息
     */
    @PostMapping(value = "/merchants/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> registerMerchantWithLogo(
            @Valid @RequestPart("data") MerchantRegisterRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return sessionResponse(userService.registerMerchant(request, logo), HttpStatus.CREATED);
    }

    /**
     * 校验登录凭据、记录失败风险并签发 JWT。
     *
     * @param request 登录请求
     * @param servletRequest 当前 HTTP 请求
     * @return 登录成功后的认证信息
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                                HttpServletRequest servletRequest) {
        String clientIp = requestGuardService.clientIp(servletRequest);
        requestGuardService.assertLoginAllowed(clientIp, request.email());
        try {
            AuthSession session = userService.login(request);
            requestGuardService.clearLoginFailures(clientIp, request.email());
            return sessionResponse(session, HttpStatus.OK);
        } catch (BusinessException exception) {
            if ("INVALID_CREDENTIALS".equals(exception.getCode())) {
                requestGuardService.recordLoginFailure(clientIp, request.email());
            }
            throw exception;
        }
    }

    /**
     * 校验图形验证码并发送找回密码邮箱验证码。
     *
     * @param request 邮箱验证码请求
     * @return 已受理的邮箱验证码回执
     */
    @PostMapping("/password-reset/email-code")
    public ResponseEntity<ApiResponse<EmailCodeResponse>> sendPasswordResetEmailCode(
            @Valid @RequestBody SendPasswordResetEmailCodeRequest request) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        EmailCodeResponse response = emailVerificationService.sendPasswordResetCode(
                request.email(), request.portalRole());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /**
     * 使用邮箱验证码重置账号密码。
     *
     * @param request 重置密码请求
     * @return 空成功响应
     */
    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ApiResponse.success(null);
    }

    /**
     * 消费 HttpOnly Cookie 中的刷新令牌并轮换认证会话。
     *
     * @param refreshToken 刷新令牌 Cookie
     * @return 新访问令牌和轮换后的刷新 Cookie
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return sessionResponse(userService.refresh(refreshToken), HttpStatus.OK);
    }

    /**
     * 撤销当前浏览器刷新会话并清除 Cookie。
     *
     * @param refreshToken 刷新令牌 Cookie
     * @return 空成功响应
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        userService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success(null));
    }

    /**
     * 确认当前 JWT 仍代表有效认证身份。
     *
     * @return 认证状态响应
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me() {
        return ApiResponse.success(Map.of("status", "authenticated"));
    }

    /**
     * 构建包含访问令牌响应和 HttpOnly 刷新 Cookie 的响应。
     *
     * @param session 认证会话
     * @param status HTTP 状态
     * @return 认证响应实体
     */
    private ResponseEntity<ApiResponse<AuthTokenResponse>> sessionResponse(AuthSession session, HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(session.refreshTokenTtlSeconds())
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(session.response()));
    }

    /**
     * 构建立即过期的刷新令牌 Cookie。
     *
     * @return 过期刷新 Cookie
     */
    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
