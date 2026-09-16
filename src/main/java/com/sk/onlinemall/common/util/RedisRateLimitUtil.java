package com.sk.onlinemall.common.util;

import com.sk.onlinemall.cache.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimitUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitUtil.class);
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建 Redis 固定窗口限流工具。
     *
     * @param redisTemplate Redis 字符串客户端
     */
    public RedisRateLimitUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断指定身份是否仍在固定窗口配额内。
     *
     * @param bucket 限流桶名称
     * @param identity 客户端或用户指纹
     * @param limit 窗口内最大请求数
     * @param window 窗口时长
     * @return 未超过配额时返回 true
     */
    public boolean isAllowed(String bucket, String identity, int limit, Duration window) {
        return increment(CacheKeys.requestRate(bucket, identity), window) <= limit;
    }

    /**
     * 原子增加 Redis 计数并在首次写入时设置过期时间。
     *
     * @param key Redis 计数键
     * @param ttl 计数有效期
     * @return 增加后的计数值，Redis 未返回结果时返回零
     */
    public long increment(String key, Duration ttl) {
        Long result = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(ttl.toSeconds()));
        if (result == null) {
            log.warn("Redis fixed-window counter returned no result for key {}", key);
            return 0;
        }
        return result;
    }
}
