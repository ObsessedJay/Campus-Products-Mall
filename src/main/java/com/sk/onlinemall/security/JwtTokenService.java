package com.sk.onlinemall.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {
    private final SecretKey signingKey;
    private final long tokenTtlSeconds;

    /**
     * 创建 JWT 签发与解析服务。
     *
     * @param secret JWT 签名密钥
     * @param tokenTtlSeconds Token 有效秒数
     */
    public JwtTokenService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.token-ttl:7200}") long tokenTtlSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * 为已认证用户签发 JWT。
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param role 用户角色
     * @return 已签名 JWT
     */
    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .claim("uid", userId)
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(tokenTtlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 校验 JWT 签名与有效期并解析声明。
     *
     * @param token JWT 字符串
     * @return JWT 声明
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
