package com.sk.onlinemall.security;

import com.sk.onlinemall.cache.CacheKeys;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.user.dto.CaptchaChallengeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class CaptchaService {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int WIDTH = 136;
    private static final int HEIGHT = 44;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    /**
     * 创建图形验证码服务。
     *
     * @param redisTemplate Redis 字符串客户端
     * @param ttlSeconds 验证码有效秒数
     */
    public CaptchaService(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.captcha-ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 删除被替换的旧验证码并生成新的图形验证码。
     *
     * @param previousCaptchaId 被替换的验证码标识，可为空
     * @return 验证码标识、图片和有效期
     */
    public CaptchaChallengeResponse createChallenge(String previousCaptchaId) {
        if (previousCaptchaId != null && !previousCaptchaId.isBlank()) {
            redisTemplate.delete(CacheKeys.captcha(previousCaptchaId.trim()));
        }
        String captchaId = UUID.randomUUID().toString();
        String answer = randomText(4);
        redisTemplate.opsForValue().set(CacheKeys.captcha(captchaId), answer, ttl);
        return new CaptchaChallengeResponse(captchaId, renderImage(answer), ttl.toSeconds());
    }

    /**
     * 校验有效期内可重复使用的图形验证码。
     *
     * @param captchaId 验证码标识
     * @param submittedCode 用户提交的验证码
     */
    public void verify(String captchaId, String submittedCode) {
        String key = CacheKeys.captcha(captchaId);
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) {
            throw new BusinessException("CAPTCHA_EXPIRED", "captcha has expired, refresh and try again");
        }
        if (submittedCode == null || !expected.equals(submittedCode.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("CAPTCHA_INVALID", "captcha code is incorrect");
        }
    }

    /**
     * 从去除易混淆字符的字符表中生成随机文本。
     *
     * @param length 文本长度
     * @return 随机验证码文本
     */
    private String randomText(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /**
     * 将验证码文本绘制为带干扰线的 PNG Data URL。
     *
     * @param answer 验证码答案
     * @return PNG 图片 Data URL
     */
    private String renderImage(String answer) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(255, 253, 247));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 6; i++) {
                graphics.setColor(i % 2 == 0 ? new Color(59, 104, 160, 105) : new Color(239, 90, 67, 90));
                graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
            for (int i = 0; i < answer.length(); i++) {
                graphics.setColor(i % 2 == 0 ? new Color(23, 63, 115) : new Color(170, 46, 32));
                int y = 31 + random.nextInt(5) - 2;
                graphics.drawString(String.valueOf(answer.charAt(i)), 13 + i * 29, y);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to render captcha", exception);
        }
    }
}
