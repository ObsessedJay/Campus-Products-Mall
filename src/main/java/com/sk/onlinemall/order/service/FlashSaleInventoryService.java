package com.sk.onlinemall.order.service;

import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.cache.CacheKeys;
import com.sk.onlinemall.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class FlashSaleInventoryService {
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[6]) == 1 then return 5 end
            if redis.call('GET', KEYS[1]) ~= 'RUNNING' then return 1 end
            if redis.call('EXISTS', KEYS[4]) == 1 and redis.call('GET', KEYS[4]) ~= 'FAILED' then return 2 end
            local stock = tonumber(redis.call('GET', KEYS[2]) or '-1')
            local quantity = tonumber(ARGV[1])
            if stock < quantity then return 3 end
            local purchased = tonumber(redis.call('GET', KEYS[3]) or '0')
            if purchased + quantity > tonumber(ARGV[2]) then return 4 end
            redis.call('DECRBY', KEYS[2], quantity)
            redis.call('INCRBY', KEYS[3], quantity)
            redis.call('SET', KEYS[4], 'PENDING', 'EX', ARGV[3])
            redis.call('SADD', KEYS[5], ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[5], ARGV[3])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local quantity = tonumber(ARGV[1])
            if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCRBY', KEYS[1], quantity) end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                local purchased = tonumber(redis.call('GET', KEYS[2]) or '0')
                local remaining = math.max(0, purchased - quantity)
                if remaining == 0 then
                    redis.call('DEL', KEYS[2])
                    redis.call('SREM', KEYS[4], ARGV[3])
                else
                    redis.call('SET', KEYS[2], tostring(remaining), 'KEEPTTL')
                end
            end
            if redis.call('EXISTS', KEYS[3]) == 1 then redis.call('SET', KEYS[3], ARGV[2], 'KEEPTTL') end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REBUILD_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            redis.call('DEL', KEYS[3])
            local argument = 4
            for keyIndex = 4, #KEYS do
                local userId = ARGV[argument]
                local quantity = tonumber(ARGV[argument + 1])
                if quantity > 0 then
                    redis.call('SET', KEYS[keyIndex], tostring(quantity), 'EX', ARGV[3])
                    redis.call('SADD', KEYS[3], userId)
                else
                    redis.call('DEL', KEYS[keyIndex])
                end
                argument = argument + 2
            end
            if redis.call('SCARD', KEYS[3]) > 0 then redis.call('EXPIRE', KEYS[3], ARGV[3]) end
            return tonumber(ARGV[2])
            """, Long.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final Duration retention;

    /**
     * 创建 FlashSaleInventoryService 实例。
     *
     * @param redisTemplate Redis 操作组件
     * @param enabled 是否启用异步抢购库存
     * @param retentionHours 抢购缓存额外保留小时数
     */
    public FlashSaleInventoryService(
            StringRedisTemplate redisTemplate,
            @Value("${app.flash-sale.enabled:true}") boolean enabled,
            @Value("${app.flash-sale.redis-retention-hours:24}") long retentionHours) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.retention = Duration.ofHours(retentionHours);
    }

    /**
     * 原子预扣活动库存并记录用户购买数量。
     *
     * @param activity 活动信息
     * @param userId 用户主键
     * @param quantity 购买数量
     * @param purchased 用户已购买数量
     * @param requestNo 业务请求号
     */
    public void reserve(FlashActivityEntity activity, long userId, int quantity, int purchased, String requestNo) {
        if (!enabled) {
            throw new BusinessException("FLASH_SALE_ASYNC_DISABLED", "flash-sale queue is disabled");
        }
        Duration ttl = ttl(activity.getEndAt());
        String stockKey = CacheKeys.flashSaleStock(activity.getId());
        String buyerKey = CacheKeys.flashSaleBuyer(activity.getId(), userId);
        redisTemplate.opsForValue().set(CacheKeys.flashSaleState(activity.getId()), "RUNNING", ttl);
        redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(activity.getStock()), ttl);
        redisTemplate.opsForValue().setIfAbsent(buyerKey, String.valueOf(purchased), ttl);

        Long result = redisTemplate.execute(RESERVE_SCRIPT,
                List.of(CacheKeys.flashSaleState(activity.getId()), stockKey, buyerKey,
                        CacheKeys.flashSaleRequest(requestNo), CacheKeys.flashSaleBuyers(activity.getId()),
                        CacheKeys.flashSaleReconciliationLock(activity.getId())),
                String.valueOf(quantity), String.valueOf(activity.getLimitPerUser()), String.valueOf(ttl.toSeconds()),
                String.valueOf(userId));
        if (result == null) {
            throw new BusinessException("FLASH_SALE_CACHE_UNAVAILABLE", "flash-sale inventory is temporarily unavailable");
        }
        switch (result.intValue()) {
            case 0 -> { return; }
            case 1 -> throw new BusinessException("ACTIVITY_NOT_AVAILABLE", "activity is not accepting orders");
            case 2 -> throw new BusinessException("FLASH_SALE_REQUEST_DUPLICATE", "request has already been accepted");
            case 3 -> throw new BusinessException("ACTIVITY_STOCK_INSUFFICIENT", "activity stock is insufficient");
            case 4 -> throw new BusinessException("ACTIVITY_LIMIT_EXCEEDED", "activity purchase limit has been reached");
            case 5 -> throw new BusinessException("INVENTORY_RECONCILIATION_RUNNING",
                    "activity inventory is being reconciled, please retry");
            default -> throw new BusinessException("FLASH_SALE_CACHE_ERROR", "unexpected flash-sale inventory result");
        }
    }

    /**
     * 释放抢购预扣库存和用户限购占用。
     *
     * @param activityId 活动主键
     * @param userId 用户主键
     * @param quantity 购买数量
     * @param requestNo 业务请求号
     * @param requestState 抢购请求状态
     */
    public void release(long activityId, long userId, int quantity, String requestNo, String requestState) {
        if (!enabled) return;
        redisTemplate.execute(RELEASE_SCRIPT,
                List.of(CacheKeys.flashSaleStock(activityId), CacheKeys.flashSaleBuyer(activityId, userId),
                        CacheKeys.flashSaleRequest(requestNo), CacheKeys.flashSaleBuyers(activityId)),
                String.valueOf(quantity), requestState, String.valueOf(userId));
    }

    /**
     * 标记成功。
     *
     * @param requestNo 业务请求号
     */
    public void markSucceeded(String requestNo) {
        if (!enabled) return;
        String key = CacheKeys.flashSaleRequest(requestNo);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().set(key, "SUCCEEDED", retention);
        }
    }

    /**
     * 当前库存。
     *
     * @param activityId 活动主键
     * @return 方法执行结果
     */
    public Integer currentStock(long activityId) {
        if (!enabled) return null;
        try {
            String value = redisTemplate.opsForValue().get(CacheKeys.flashSaleStock(activityId));
            return value == null ? null : Integer.valueOf(value);
        } catch (DataAccessException | NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 按照数据库事实重建活动库存和用户限购缓存。
     *
     * @param activity 活动信息
     * @param expectedStock 期望库存数量
     * @param buyerQuantities 用户累计购买数量映射
     */
    public void rebuild(FlashActivityEntity activity, int expectedStock, Map<Long, Integer> buyerQuantities) {
        if (!enabled) {
            throw new BusinessException("FLASH_SALE_ASYNC_DISABLED", "flash-sale cache is disabled");
        }
        if (expectedStock < 0) {
            throw new BusinessException("INVENTORY_RECONCILIATION_INVALID",
                    "pending quantity exceeds database inventory");
        }
        Duration ttl = ttl(activity.getEndAt());
        Set<String> previousBuyers = redisTemplate.opsForSet().members(CacheKeys.flashSaleBuyers(activity.getId()));
        Set<Long> allBuyerIds = new LinkedHashSet<>(buyerQuantities.keySet());
        if (previousBuyers != null) {
            for (String value : previousBuyers) {
                try { allBuyerIds.add(Long.valueOf(value)); }
                catch (NumberFormatException ignored) { }
            }
        }

        List<String> keys = new ArrayList<>();
        keys.add(CacheKeys.flashSaleState(activity.getId()));
        keys.add(CacheKeys.flashSaleStock(activity.getId()));
        keys.add(CacheKeys.flashSaleBuyers(activity.getId()));
        List<String> arguments = new ArrayList<>();
        arguments.add(isRunning(activity) ? "RUNNING" : "CLOSED");
        arguments.add(String.valueOf(expectedStock));
        arguments.add(String.valueOf(ttl.toSeconds()));
        for (Long userId : allBuyerIds) {
            keys.add(CacheKeys.flashSaleBuyer(activity.getId(), userId));
            arguments.add(String.valueOf(userId));
            arguments.add(String.valueOf(buyerQuantities.getOrDefault(userId, 0)));
        }
        Long result = redisTemplate.execute(REBUILD_SCRIPT, keys, arguments.toArray());
        if (result == null || result.intValue() != expectedStock) {
            throw new BusinessException("INVENTORY_RECONCILIATION_FAILED", "failed to rebuild Redis inventory");
        }
    }

    /**
     * 获取活动库存校准锁。
     *
     * @param activityId 活动主键
     * @return 方法执行结果
     */
    public String acquireReconciliationLock(long activityId) {
        if (!enabled) {
            throw new BusinessException("FLASH_SALE_ASYNC_DISABLED", "flash-sale cache is disabled");
        }
        String token = java.util.UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    CacheKeys.flashSaleReconciliationLock(activityId), token, Duration.ofSeconds(30));
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (DataAccessException exception) {
            throw new BusinessException("FLASH_SALE_CACHE_UNAVAILABLE",
                    "flash-sale inventory is temporarily unavailable");
        }
    }

    /**
     * 安全释放活动库存校准锁。
     *
     * @param activityId 活动主键
     * @param token 身份令牌或锁令牌
     */
    public void releaseReconciliationLock(long activityId, String token) {
        if (!enabled || token == null) return;
        redisTemplate.execute(UNLOCK_SCRIPT,
                List.of(CacheKeys.flashSaleReconciliationLock(activityId)), token);
    }

    /**
     * 计算抢购缓存的有效时长。
     *
     * @param activityEnd 活动结束时间
     * @return 方法执行结果
     */
    private Duration ttl(LocalDateTime activityEnd) {
        long secondsUntilEnd = activityEnd.toEpochSecond(ZoneOffset.ofHours(8))
                - LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));
        return Duration.ofSeconds(Math.max(60, secondsUntilEnd + retention.toSeconds()));
    }

    /**
     * 判断活动是否处于抢购进行中。
     *
     * @param activity 活动信息
     * @return 是否满足业务条件
     */
    private boolean isRunning(FlashActivityEntity activity) {
        LocalDateTime now = LocalDateTime.now();
        return activity.getStartAt() != null && activity.getEndAt() != null
                && !now.isBefore(activity.getStartAt()) && now.isBefore(activity.getEndAt())
                && activity.getStatus() != com.sk.onlinemall.activity.model.ActivityStatus.ENDED
                && activity.getStatus() != com.sk.onlinemall.activity.model.ActivityStatus.TERMINATED;
    }
}
