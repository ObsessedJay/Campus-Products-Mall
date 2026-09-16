package com.sk.onlinemall.dashboard;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.dashboard.mapper.DashboardMapper;
import com.sk.onlinemall.dashboard.model.DashboardAggregate;
import com.sk.onlinemall.dashboard.service.DashboardService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTests {
    /**
     * 验证核销率按可核销订单计算并保留两位小数。
     */
    @Test
    void shouldCalculatePickupRate() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        DashboardAggregate aggregate = new DashboardAggregate();
        aggregate.setUserCount(1L);
        aggregate.setProductCount(2L);
        aggregate.setActivityCount(3L);
        aggregate.setReservationCount(4L);
        aggregate.setOrderCount(5L);
        aggregate.setPaidAmount(BigDecimal.TEN);
        aggregate.setPickupEligibleCount(3L);
        aggregate.setPickedUpCount(2L);
        when(mapper.aggregate(any(), any(), isNull(), isNull())).thenReturn(aggregate);

        var metrics = new DashboardService(mapper).metrics(LocalDate.now(), LocalDate.now(), null, null);

        assertEquals(new BigDecimal("66.67"), metrics.pickupRate());
    }

    /**
     * 验证倒置日期范围被拒绝。
     */
    @Test
    void shouldRejectInvalidDateRange() {
        DashboardService service = new DashboardService(mock(DashboardMapper.class));
        assertThrows(BusinessException.class,
                () -> service.metrics(LocalDate.now(), LocalDate.now().minusDays(1), null, null));
    }
}
