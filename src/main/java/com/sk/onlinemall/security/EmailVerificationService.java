package com.sk.onlinemall.security;

import com.sk.onlinemall.cache.CacheKeys;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.user.dto.EmailCodeResponse;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class EmailVerificationService {
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final SecureRandom random = new SecureRandom();
    private final String sender;
    private final Duration codeTtl;
    private final Duration cooldown;

    /**
     * 创建 EmailVerificationService 实例。
     *
     * @param mailSender 邮件发送组件
     * @param redisTemplate Redis 操作组件
     * @param userMapper 用户数据访问组件
     * @param sender 发件人邮箱
     * @param codeTtlSeconds 验证码有效秒数
     * @param cooldownSeconds 重复发送冷却秒数
     */
    public EmailVerificationService(
            JavaMailSender mailSender,
            StringRedisTemplate redisTemplate,
            UserMapper userMapper,
            @Value("${spring.mail.username:}") String sender,
            @Value("${app.auth.email-code-ttl-seconds:180}") long codeTtlSeconds,
            @Value("${app.auth.email-code-cooldown-seconds:60}") long cooldownSeconds) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.sender = sender;
        this.codeTtl = Duration.ofSeconds(codeTtlSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /**
     * 发送注册邮件验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @return 方法执行结果
     */
    public EmailCodeResponse sendRegistrationCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (userMapper.findByEmailAndRole(email, UserRole.STUDENT) != null) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        return sendCode(email, CacheKeys.emailCode(email), CacheKeys.emailCodeCooldown(email),
                "校集学生账号注册验证码", "你的注册验证码是：");
    }

    /**
     * 发送商家注册邮件验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @return 邮箱验证码回执
     */
    public EmailCodeResponse sendMerchantRegistrationCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (findPortalAccount(email, "MERCHANT") != null) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        return sendCode(email, CacheKeys.merchantEmailCode(email), CacheKeys.merchantEmailCodeCooldown(email),
                "校集商家账号注册验证码", "你的商家注册验证码是：");
    }

    /**
     * 向已注册邮箱发送找回密码验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @param portalRole 账号入口角色
     * @return 邮箱验证码回执
     */
    public EmailCodeResponse sendPasswordResetCode(String rawEmail, String portalRole) {
        String email = normalize(rawEmail);
        if (findPortalAccount(email, portalRole) == null) {
            // 对不存在的邮箱返回相同回执，避免认证接口泄露账号注册状态。
            return new EmailCodeResponse(codeTtl.toSeconds(), cooldown.toSeconds());
        }
        return sendCode(email, CacheKeys.passwordResetEmailCode(email, portalRole),
                CacheKeys.passwordResetEmailCodeCooldown(email, portalRole),
                "校集账号密码重置验证码", "你的密码重置验证码是：");
    }

    /**
     * 校验并消费注册邮件验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @param submittedCode 用户提交的邮件验证码
     */
    public void verifyRegistrationCode(String rawEmail, String submittedCode) {
        String email = normalize(rawEmail);
        verifyCode(CacheKeys.emailCode(email), submittedCode);
    }

    /**
     * 校验并消费商家注册邮件验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @param submittedCode 用户提交的邮件验证码
     */
    public void verifyMerchantRegistrationCode(String rawEmail, String submittedCode) {
        verifyCode(CacheKeys.merchantEmailCode(normalize(rawEmail)), submittedCode);
    }

    /**
     * 校验并消费找回密码邮件验证码。
     *
     * @param rawEmail 未规范化的邮箱地址
     * @param portalRole 账号入口角色
     * @param submittedCode 用户提交的邮件验证码
     */
    public void verifyPasswordResetCode(String rawEmail, String portalRole, String submittedCode) {
        String email = normalize(rawEmail);
        verifyCode(CacheKeys.passwordResetEmailCode(email, portalRole), submittedCode);
    }

    /**
     * 按入口查询账号，商家入口兼容旧版运营角色。
     *
     * @param email 规范化邮箱地址
     * @param portalRole 账号入口角色
     * @return 对应账号，不存在时返回空
     */
    private UserEntity findPortalAccount(String email, String portalRole) {
        if ("MERCHANT".equals(portalRole)) {
            UserEntity merchant = userMapper.findByEmailAndRole(email, UserRole.MERCHANT);
            return merchant != null ? merchant : userMapper.findByEmailAndRole(email, UserRole.OPERATOR);
        }
        if ("ADMIN".equals(portalRole)) {
            return userMapper.findByEmailAndRole(email, UserRole.ADMIN);
        }
        return userMapper.findByEmailAndRole(email, UserRole.STUDENT);
    }

    /**
     * 生成并发送指定用途的一次性邮箱验证码。
     *
     * @param email 规范化邮箱地址
     * @param codeKey 验证码缓存键
     * @param cooldownKey 发送冷却缓存键
     * @param subject 邮件主题
     * @param contentPrefix 邮件正文前缀
     * @return 邮箱验证码回执
     */
    private EmailCodeResponse sendCode(String email, String codeKey, String cooldownKey,
                                       String subject, String contentPrefix) {
        if (sender == null || sender.isBlank()) {
            throw new BusinessException("EMAIL_SERVICE_NOT_CONFIGURED", "email verification service is not configured");
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", cooldown);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("EMAIL_CODE_TOO_FREQUENT", "please wait before requesting another email code");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey, code, codeTtl);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(contentPrefix + code + "\n\n验证码 " + codeTtl.toMinutes()
                    + " 分钟内有效。若非本人操作，请忽略此邮件。");
            mailSender.send(message);
        } catch (MailException exception) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw new BusinessException("EMAIL_SEND_FAILED", "verification email could not be sent, try again later");
        }
        return new EmailCodeResponse(codeTtl.toSeconds(), cooldown.toSeconds());
    }

    /**
     * 原子读取并消费一次性邮箱验证码。
     *
     * @param key 验证码缓存键
     * @param submittedCode 用户提交的验证码
     */
    private void verifyCode(String key, String submittedCode) {
        String expected = redisTemplate.opsForValue().getAndDelete(key);
        if (expected == null) {
            throw new BusinessException("EMAIL_CODE_EXPIRED", "email verification code has expired");
        }
        if (submittedCode == null || !expected.equals(submittedCode.trim())) {
            throw new BusinessException("EMAIL_CODE_INVALID", "email verification code is incorrect");
        }
    }

    /**
     * 去除文本首尾空白并将空文本转为空值。
     *
     * @param email 邮箱地址
     * @return 转换后的结果
     */
    private String normalize(String email) {
        return TextUtil.normalizeLowercase(email);
    }
}
