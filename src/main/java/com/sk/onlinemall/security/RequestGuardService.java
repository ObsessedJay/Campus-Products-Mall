package com.sk.onlinemall.security;

import com.sk.onlinemall.cache.CacheKeys;
import com.sk.onlinemall.common.util.DigestUtil;
import com.sk.onlinemall.common.util.RedisRateLimitUtil;
import com.sk.onlinemall.common.util.TextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.service.SystemConfigService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RequestGuardService {
    private static final Logger log = LoggerFactory.getLogger(RequestGuardService.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimitUtil rateLimitUtil;
    private final boolean enabled;
    private final int authMaxRequests;
    private final Duration authWindow;
    private final int writeMaxRequests;
    private final Duration writeWindow;
    private final int flashSaleMaxRequests;
    private final Duration flashSaleWindow;
    private final int loginFailureLimit;
    private final Duration loginBlockDuration;
    private final SystemConfigService systemConfigService;

    /**
     * 创建请求风控服务。
     *
     * @param redisTemplate Redis 字符串客户端
     * @param rateLimitUtil Redis 固定窗口限流工具
     * @param enabled 是否启用请求风控
     * @param authMaxRequests 认证窗口最大请求数
     * @param authWindowSeconds 认证窗口秒数
     * @param writeMaxRequests 写接口窗口最大请求数
     * @param writeWindowSeconds 写接口窗口秒数
     * @param flashSaleMaxRequests 抢购窗口最大请求数
     * @param flashSaleWindowSeconds 抢购窗口秒数
     * @param loginFailureLimit 登录失败拉黑阈值
     * @param loginBlockSeconds 登录失败拉黑秒数
     * @param systemConfigService 系统动态配置服务
     */
    @Autowired
    public RequestGuardService(
            StringRedisTemplate redisTemplate,
            RedisRateLimitUtil rateLimitUtil,
            @Value("${app.security.request-guard.enabled:true}") boolean enabled,
            @Value("${app.security.request-guard.auth-max-requests:30}") int authMaxRequests,
            @Value("${app.security.request-guard.auth-window-seconds:60}") long authWindowSeconds,
            @Value("${app.security.request-guard.write-max-requests:120}") int writeMaxRequests,
            @Value("${app.security.request-guard.write-window-seconds:60}") long writeWindowSeconds,
            @Value("${app.security.request-guard.flash-sale-max-requests:10}") int flashSaleMaxRequests,
            @Value("${app.security.request-guard.flash-sale-window-seconds:1}") long flashSaleWindowSeconds,
            @Value("${app.security.request-guard.login-failure-limit:5}") int loginFailureLimit,
            @Value("${app.security.request-guard.login-block-seconds:900}") long loginBlockSeconds,
            SystemConfigService systemConfigService) {
        this.redisTemplate = redisTemplate;
        this.rateLimitUtil = rateLimitUtil;
        this.enabled = enabled;
        this.authMaxRequests = authMaxRequests;
        this.authWindow = Duration.ofSeconds(authWindowSeconds);
        this.writeMaxRequests = writeMaxRequests;
        this.writeWindow = Duration.ofSeconds(writeWindowSeconds);
        this.flashSaleMaxRequests = flashSaleMaxRequests;
        this.flashSaleWindow = Duration.ofSeconds(flashSaleWindowSeconds);
        this.loginFailureLimit = loginFailureLimit;
        this.loginBlockDuration = Duration.ofSeconds(loginBlockSeconds);
        this.systemConfigService = systemConfigService;
    }

    /**
     * 创建用于独立测试的请求风控服务。
     *
     * @param redisTemplate Redis 字符串客户端
     * @param enabled 是否启用请求风控
     * @param authMaxRequests 认证窗口最大请求数
     * @param authWindowSeconds 认证窗口秒数
     * @param writeMaxRequests 写接口窗口最大请求数
     * @param writeWindowSeconds 写接口窗口秒数
     * @param flashSaleMaxRequests 抢购窗口最大请求数
     * @param flashSaleWindowSeconds 抢购窗口秒数
     * @param loginFailureLimit 登录失败拉黑阈值
     * @param loginBlockSeconds 登录失败拉黑秒数
     */
    RequestGuardService(StringRedisTemplate redisTemplate, boolean enabled, int authMaxRequests,
                        long authWindowSeconds, int writeMaxRequests, long writeWindowSeconds,
                        int flashSaleMaxRequests, long flashSaleWindowSeconds,
                        int loginFailureLimit, long loginBlockSeconds) {
        this.redisTemplate = redisTemplate;
        this.rateLimitUtil = new RedisRateLimitUtil(redisTemplate);
        this.enabled = enabled;
        this.authMaxRequests = authMaxRequests;
        this.authWindow = Duration.ofSeconds(authWindowSeconds);
        this.writeMaxRequests = writeMaxRequests;
        this.writeWindow = Duration.ofSeconds(writeWindowSeconds);
        this.flashSaleMaxRequests = flashSaleMaxRequests;
        this.flashSaleWindow = Duration.ofSeconds(flashSaleWindowSeconds);
        this.loginFailureLimit = loginFailureLimit;
        this.loginBlockDuration = Duration.ofSeconds(loginBlockSeconds);
        this.systemConfigService = null;
    }

    /**
     * 判断请求是否需要进入限流过滤器。
     *
     * @param request HTTP 请求
     * @return 需要风控时返回 true
     */
    public boolean isGuardedRequest(HttpServletRequest request) {
        if (!enabled) return false;
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/auth/")) return true;
        return uri.startsWith("/api/v1/") && !isSafeMethod(request.getMethod());
    }

    /**
     * 校验单次 HTTP 请求的黑名单和访问频率。
     *
     * @param request HTTP 请求
     * @param username 已认证用户名，未登录时为空
     */
    public void checkRequest(HttpServletRequest request, String username) {
        if (!enabled) return;
        String ip = clientIp(request);
        try {
            assertNotBlacklisted(ip, username);
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/v1/auth/")) {
                enforceRate(authBucket(uri), fingerprint(ip), authMaxRequests, authWindow);
                return;
            }
            String identity = fingerprint(username == null || username.isBlank() ? ip : normalize(username));
            if (HttpMethod.POST.matches(request.getMethod())
                    && "/api/v1/orders/flash-sale".equals(uri)) {
                enforceRate("flash-sale", identity,
                        dynamic(SystemConfigKey.FLASH_SALE_RATE_LIMIT, flashSaleMaxRequests), flashSaleWindow);
                return;
            }
            enforceRate("write", identity, dynamic(SystemConfigKey.WRITE_RATE_LIMIT, writeMaxRequests), writeWindow);
        } catch (DataAccessException exception) {
            log.warn("Request guard Redis check failed; allowing request to continue", exception);
        }
    }

    /**
     * 在登录校验前检查 IP 和用户名黑名单。
     *
     * @param ip 客户端 IP
     * @param username 登录用户名
     */
    public void assertLoginAllowed(String ip, String username) {
        if (!enabled) return;
        try {
            assertNotBlacklisted(ip, username);
        } catch (DataAccessException exception) {
            log.warn("Login blacklist check failed; allowing authentication to continue", exception);
        }
    }

    /**
     * 记录一次账号密码校验失败并在达到阈值后临时拉黑。
     *
     * @param ip 客户端 IP
     * @param username 登录用户名
     */
    public void recordLoginFailure(String ip, String username) {
        if (!enabled) return;
        String ipFingerprint = fingerprint(ip);
        String userFingerprint = fingerprint(normalize(username));
        try {
            long ipFailures = rateLimitUtil.increment(
                    CacheKeys.loginFailure("ip", ipFingerprint), loginBlockDuration);
            long userFailures = rateLimitUtil.increment(
                    CacheKeys.loginFailure("user", userFingerprint), loginBlockDuration);
            int failureLimit = dynamic(SystemConfigKey.LOGIN_FAILURE_LIMIT, loginFailureLimit);
            if (ipFailures >= failureLimit) blacklist("ip", ipFingerprint);
            if (userFailures >= failureLimit) blacklist("user", userFingerprint);
        } catch (DataAccessException exception) {
            log.warn("Failed to record authentication risk signal", exception);
        }
    }

    /**
     * 登录成功后清理尚未触发拉黑的失败计数。
     *
     * @param ip 客户端 IP
     * @param username 登录用户名
     */
    public void clearLoginFailures(String ip, String username) {
        if (!enabled) return;
        try {
            redisTemplate.delete(List.of(
                    CacheKeys.loginFailure("ip", fingerprint(ip)),
                    CacheKeys.loginFailure("user", fingerprint(normalize(username)))));
        } catch (DataAccessException exception) {
            log.warn("Failed to clear authentication risk counters", exception);
        }
    }

    /**
     * 获取当前直连客户端 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP，缺失时返回 unknown
     */
    public String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    /**
     * 校验 IP 和用户是否位于临时黑名单。
     *
     * @param ip 客户端 IP
     * @param username 用户名，可为空
     */
    private void assertNotBlacklisted(String ip, String username) {
        boolean ipBlocked = Boolean.TRUE.equals(redisTemplate.hasKey(
                CacheKeys.requestBlacklist("ip", fingerprint(ip))));
        boolean userBlocked = username != null && !username.isBlank() && Boolean.TRUE.equals(redisTemplate.hasKey(
                CacheKeys.requestBlacklist("user", fingerprint(normalize(username)))));
        if (ipBlocked || userBlocked) {
            throw new RequestGuardException(HttpStatus.FORBIDDEN, "REQUEST_BLACKLISTED",
                    "request has been temporarily blocked due to abnormal activity");
        }
    }

    /**
     * 执行固定窗口原子限流。
     *
     * @param bucket 限流策略分组
     * @param identity 客户端或用户指纹
     * @param limit 窗口内最大请求数
     * @param window 窗口时长
     */
    private void enforceRate(String bucket, String identity, int limit, Duration window) {
        if (!rateLimitUtil.isAllowed(bucket, identity, limit, window)) {
            throw new RequestGuardException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "too many requests, please try again later");
        }
    }

    /**
     * 根据认证接口选择相互独立的限流桶。
     *
     * @param uri 认证接口路径
     * @return 对应的认证限流桶名称
     */
    private String authBucket(String uri) {
        return switch (uri) {
            case "/api/v1/auth/captcha" -> "auth:captcha";
            case "/api/v1/auth/login" -> "auth:login";
            case "/api/v1/auth/register" -> "auth:student-register";
            case "/api/v1/auth/email-codes" -> "auth:student-email-code";
            case "/api/v1/auth/merchants/register" -> "auth:merchant-register";
            case "/api/v1/auth/merchants/email-codes" -> "auth:merchant-email-code";
            case "/api/v1/auth/password-reset/email-code" -> "auth:password-reset-email-code";
            case "/api/v1/auth/password-reset" -> "auth:password-reset";
            case "/api/v1/auth/refresh" -> "auth:refresh";
            case "/api/v1/auth/logout" -> "auth:logout";
            case "/api/v1/auth/me" -> "auth:me";
            default -> "auth:other";
        };
    }

    /**
     * 写入临时黑名单标记。
     *
     * @param scope 黑名单维度
     * @param identity IP 或用户名指纹
     */
    private void blacklist(String scope, String identity) {
        redisTemplate.opsForValue().set(CacheKeys.requestBlacklist(scope, identity), "1", loginBlockDuration);
    }

    /**
     * 读取即时生效的动态阈值。
     *
     * @param key 配置键
     * @param fallback 启动配置兜底值
     * @return 当前阈值
     */
    private int dynamic(SystemConfigKey key, int fallback) {
        return systemConfigService == null ? fallback : systemConfigService.getInt(key, fallback);
    }

    /**
     * 判断 HTTP 方法是否只读。
     *
     * @param method HTTP 方法名称
     * @return 只读方法返回 true
     */
    private boolean isSafeMethod(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method);
    }

    /**
     * 规范化用户标识以确保同一账号使用一致计数键。
     *
     * @param value 原始用户标识
     * @return 小写且去除首尾空白的用户标识
     */
    private String normalize(String value) {
        return TextUtil.normalizeLowercase(value);
    }

    /**
     * 对敏感标识生成不可逆摘要，避免将 IP 或用户名写入 Redis 键。
     *
     * @param value 原始标识
     * @return SHA-256 十六进制摘要
     */
    private String fingerprint(String value) {
        return DigestUtil.sha256Hex(normalize(value));
    }
}
