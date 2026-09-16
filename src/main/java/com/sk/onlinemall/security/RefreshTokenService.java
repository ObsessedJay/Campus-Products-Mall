package com.sk.onlinemall.security;

import com.sk.onlinemall.cache.CacheKeys;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration tokenTtl;

    /**
     * 创建刷新令牌服务。
     *
     * @param redisTemplate Redis 操作组件
     * @param tokenTtlSeconds 刷新令牌有效秒数
     */
    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.refresh-token-ttl:604800}") long tokenTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
    }

    /**
     * 为用户创建仅可使用一次的刷新令牌。
     *
     * @param userId 用户标识
     * @return 原始刷新令牌及有效期
     */
    public IssuedRefreshToken issue(long userId) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String digest = DigestUtil.sha256Hex(token);
        redisTemplate.opsForValue().set(CacheKeys.refreshToken(digest), Long.toString(userId), tokenTtl);
        String userTokensKey = CacheKeys.userRefreshTokens(userId);
        redisTemplate.opsForSet().add(userTokensKey, digest);
        redisTemplate.expire(userTokensKey, tokenTtl);
        return new IssuedRefreshToken(token, tokenTtl.toSeconds());
    }

    /**
     * 原子消费刷新令牌并返回所属用户。
     *
     * @param token 原始刷新令牌
     * @return 令牌所属用户标识
     */
    public long consume(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        String digest = DigestUtil.sha256Hex(token);
        String userIdValue = redisTemplate.opsForValue().getAndDelete(CacheKeys.refreshToken(digest));
        if (userIdValue == null) {
            throw invalidToken();
        }
        try {
            long userId = Long.parseLong(userIdValue);
            redisTemplate.opsForSet().remove(CacheKeys.userRefreshTokens(userId), digest);
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }
    }

    /**
     * 撤销一个尚未消费的刷新令牌。
     *
     * @param token 原始刷新令牌
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String digest = DigestUtil.sha256Hex(token);
        String userIdValue = redisTemplate.opsForValue().getAndDelete(CacheKeys.refreshToken(digest));
        if (userIdValue == null) {
            return;
        }
        try {
            long userId = Long.parseLong(userIdValue);
            redisTemplate.opsForSet().remove(CacheKeys.userRefreshTokens(userId), digest);
        } catch (NumberFormatException ignored) {
            // 损坏的缓存值已被删除，无需阻断退出流程。
        }
    }

    /**
     * 撤销指定用户的全部刷新会话。
     *
     * @param userId 用户标识
     */
    public void revokeAll(long userId) {
        String userTokensKey = CacheKeys.userRefreshTokens(userId);
        Set<String> digests = redisTemplate.opsForSet().members(userTokensKey);
        if (digests != null && !digests.isEmpty()) {
            redisTemplate.delete(digests.stream().map(CacheKeys::refreshToken).toList());
        }
        redisTemplate.delete(userTokensKey);
    }

    /**
     * 创建统一的刷新令牌失效异常。
     *
     * @return 刷新令牌失效异常
     */
    private BusinessException invalidToken() {
        return new BusinessException("REFRESH_TOKEN_INVALID", "refresh session is invalid or expired");
    }

    public record IssuedRefreshToken(String token, long expiresInSeconds) {
    }
}
