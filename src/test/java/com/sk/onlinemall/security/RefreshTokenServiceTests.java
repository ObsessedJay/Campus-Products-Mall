package com.sk.onlinemall.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTests {

    /**
     * 验证刷新令牌仅以摘要保存并且只能消费一次。
     */
    @Test
    void shouldStoreDigestAndConsumeRefreshTokenOnce() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        RefreshTokenService service = new RefreshTokenService(redisTemplate, 600);

        RefreshTokenService.IssuedRefreshToken issued = service.issue(42L);

        assertThat(issued.token()).hasSize(43);
        assertThat(issued.expiresInSeconds()).isEqualTo(600);
        verify(values).set(any(String.class), eq("42"), eq(Duration.ofSeconds(600)));
        verify(sets).add(any(String.class), any(String.class));

        String digestKey = "campus-creative:auth:refresh-token:"
                + java.util.HexFormat.of().formatHex(sha256(issued.token()));
        when(values.getAndDelete(digestKey)).thenReturn("42", (String) null);
        assertThat(service.consume(issued.token())).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.consume(issued.token()))
                .isInstanceOf(com.sk.onlinemall.common.exception.BusinessException.class)
                .hasMessageContaining("invalid or expired");
    }

    /**
     * 计算测试令牌的 SHA-256 摘要。
     *
     * @param value 原始令牌
     * @return 摘要字节
     */
    private byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
