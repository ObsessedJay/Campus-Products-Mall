package com.sk.onlinemall.security;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.user.dto.CaptchaChallengeResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTests {

    /**
     * 验证 PNG 图形验证码在有效期内可重复校验。
     */
    @Test
    void shouldCreatePngChallengeAndReuseAnswerBeforeExpiry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        CaptchaService service = new CaptchaService(redisTemplate, 300);

        CaptchaChallengeResponse challenge = service.createChallenge(null);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).set(keyCaptor.capture(), answerCaptor.capture(), any(Duration.class));

        assertThat(challenge.imageData()).startsWith("data:image/png;base64,");
        assertThat(challenge.expiresInSeconds()).isEqualTo(300);
        assertThat(answerCaptor.getValue()).hasSize(4);

        when(values.get(keyCaptor.getValue())).thenReturn(answerCaptor.getValue());
        service.verify(challenge.captchaId(), answerCaptor.getValue().toLowerCase());
        service.verify(challenge.captchaId(), answerCaptor.getValue().toLowerCase());
        verify(values, org.mockito.Mockito.times(2)).get(keyCaptor.getValue());
    }

    /**
     * 验证刷新图形验证码时立即删除旧缓存键。
     */
    @Test
    void shouldDeletePreviousChallengeWhenCreatingReplacement() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        CaptchaService service = new CaptchaService(redisTemplate, 300);

        service.createChallenge("old-captcha-id");

        verify(redisTemplate).delete("campus-creative:auth:captcha:old-captcha-id");
    }

    /**
     * 验证驳回已过期挑战。
     */
    @Test
    void shouldRejectExpiredChallenge() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        CaptchaService service = new CaptchaService(redisTemplate, 300);

        assertThatThrownBy(() -> service.verify("missing", "ABCD"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CAPTCHA_EXPIRED"));
    }

}
