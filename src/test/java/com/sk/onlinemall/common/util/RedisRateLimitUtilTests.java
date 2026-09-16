package com.sk.onlinemall.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimitUtilTests {

    /**
     * 验证固定窗口计数超过上限时拒绝请求并使用指定桶名称。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectRequestWhenFixedWindowLimitIsExceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("60"))).thenReturn(31L);
        RedisRateLimitUtil rateLimitUtil = new RedisRateLimitUtil(redisTemplate);

        boolean allowed = rateLimitUtil.isAllowed("auth:login", "identity", 30, Duration.ofSeconds(60));

        assertThat(allowed).isFalse();
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("campus-creative:risk:rate:auth:login:identity")), eq("60"));
    }
}
