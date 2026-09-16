package com.sk.onlinemall.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestGuardServiceTests {

    /**
     * 验证抢购请求超过独立窗口阈值后返回限流异常。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRateLimitFlashSaleRequestsByAuthenticatedUser() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(2L);
        RequestGuardService service = service(redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/flash-sale");
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> service.checkRequest(request, "student001"))
                .isInstanceOfSatisfying(RequestGuardException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getCode()).isEqualTo("RATE_LIMITED");
                });
    }

    /**
     * 验证只读商品查询不会进入请求风控范围。
     */
    @Test
    void shouldSkipPublicReadRequests() {
        RequestGuardService service = service(mock(StringRedisTemplate.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");

        assertThat(service.isGuardedRequest(request)).isFalse();
    }

    /**
     * 验证同一 IP 的验证码和登录请求使用不同的认证限流桶。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUseIndependentBucketsForAuthenticationRoutes() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        RequestGuardService service = service(redisTemplate);
        MockHttpServletRequest captcha = new MockHttpServletRequest("GET", "/api/v1/auth/captcha");
        captcha.setRemoteAddr("127.0.0.1");
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        login.setRemoteAddr("127.0.0.1");

        service.checkRequest(captcha, null);
        service.checkRequest(login, null);

        verify(redisTemplate).execute(any(RedisScript.class),
                argThat(keys -> ((List<String>) keys).get(0).contains(":rate:auth:captcha:")), eq("60"));
        verify(redisTemplate).execute(any(RedisScript.class),
                argThat(keys -> ((List<String>) keys).get(0).contains(":rate:auth:login:")), eq("60"));
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), eq("60"));
    }

    /**
     * 验证命中临时 IP 黑名单时立即拒绝认证请求。
     */
    @Test
    void shouldRejectBlacklistedIp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        RequestGuardService service = service(redisTemplate);

        assertThatThrownBy(() -> service.assertLoginAllowed("127.0.0.1", "student001"))
                .isInstanceOfSatisfying(RequestGuardException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("REQUEST_BLACKLISTED");
                });
    }

    /**
     * 验证连续登录失败达到阈值后同时写入 IP 和用户黑名单。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldBlacklistIpAndUserAfterRepeatedLoginFailures() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(3L);
        when(redisTemplate.opsForValue()).thenReturn(values);
        RequestGuardService service = service(redisTemplate);

        service.recordLoginFailure("127.0.0.1", "Student001");

        verify(values).set(org.mockito.ArgumentMatchers.contains(":blacklist:ip:"),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(900)));
        verify(values).set(org.mockito.ArgumentMatchers.contains(":blacklist:user:"),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(900)));
    }

    /**
     * 创建使用短阈值的风控服务测试实例。
     *
     * @param redisTemplate Redis 模拟客户端
     * @return 风控服务实例
     */
    private RequestGuardService service(StringRedisTemplate redisTemplate) {
        return new RequestGuardService(redisTemplate, true,
                2, 60, 3, 60, 1, 1, 3, 900);
    }
}
