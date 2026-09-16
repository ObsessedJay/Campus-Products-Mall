package com.sk.onlinemall.order;

import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.order.service.FlashSaleInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashSaleInventoryServiceTests {
    private StringRedisTemplate redisTemplate;
    private FlashSaleInventoryService inventoryService;
    private FlashActivityEntity activity;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        inventoryService = new FlashSaleInventoryService(redisTemplate, true, 24);
        activity = new FlashActivityEntity();
        activity.setId(901L);
        activity.setStock(2);
        activity.setLimitPerUser(1);
        activity.setEndAt(LocalDateTime.now().plusHours(1));
    }

    /**
     * 验证 Lua 抢购预扣成功结果映射正确。
     */
    @Test
    void shouldAcceptSuccessfulLuaReservation() {
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);

        assertThatCode(() -> inventoryService.reserve(activity, 11L, 1, 0, "request-ok"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证 Lua 库存不足和超限结果映射正确。
     */
    @Test
    void shouldMapLuaStockAndLimitFailures() {
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3L, 4L);

        assertThatThrownBy(() -> inventoryService.reserve(activity, 11L, 1, 0, "request-stock"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ACTIVITY_STOCK_INSUFFICIENT");
        assertThatThrownBy(() -> inventoryService.reserve(activity, 11L, 1, 0, "request-limit"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ACTIVITY_LIMIT_EXCEEDED");
    }
}
