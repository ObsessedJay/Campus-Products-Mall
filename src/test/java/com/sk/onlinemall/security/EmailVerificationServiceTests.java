package com.sk.onlinemall.security;

import com.sk.onlinemall.user.dto.EmailCodeResponse;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTests {

    /**
     * 验证注册邮件验证码可发送并一次性消费。
     */
    @Test
    void shouldSendAndConsumeRegistrationCode() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserMapper userMapper = mock(UserMapper.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);
        EmailVerificationService service = new EmailVerificationService(
                mailSender, redisTemplate, userMapper, "sender@qq.com", 600, 60);

        EmailCodeResponse response = service.sendRegistrationCode("Student@QQ.com");
        ArgumentCaptor<String> codeKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).set(codeKeyCaptor.capture(), codeCaptor.capture(), any(Duration.class));
        verify(mailSender).send(any(SimpleMailMessage.class));

        assertThat(response.expiresInSeconds()).isEqualTo(600);
        assertThat(response.retryAfterSeconds()).isEqualTo(60);
        assertThat(codeCaptor.getValue()).matches("\\d{6}");

        when(values.getAndDelete(codeKeyCaptor.getValue())).thenReturn(codeCaptor.getValue());
        service.verifyRegistrationCode("student@qq.com", codeCaptor.getValue());
        verify(values).getAndDelete(codeKeyCaptor.getValue());
    }
}
