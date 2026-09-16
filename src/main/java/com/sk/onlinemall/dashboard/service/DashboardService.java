package com.sk.onlinemall.dashboard.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.dashboard.mapper.DashboardMapper;
import com.sk.onlinemall.dashboard.model.DashboardAggregate;
import com.sk.onlinemall.dashboard.model.DashboardMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DashboardService {
    private final DashboardMapper dashboardMapper;

    /**
     * 创建运营看板服务。
     *
     * @param dashboardMapper 看板聚合组件
     */
    public DashboardService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    /**
     * 查询指定日期及业务维度的看板指标。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param activityId 可选活动主键
     * @param productId 可选商品主键
     * @return 看板指标
     */
    public DashboardMetrics metrics(LocalDate startDate, LocalDate endDate, Long activityId, Long productId) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(29) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        if (end.isBefore(start) || start.plusYears(2).isBefore(end)) {
            throw new BusinessException("INVALID_DATE_RANGE", "dashboard date range is invalid");
        }
        DashboardAggregate value = dashboardMapper.aggregate(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                activityId, productId);
        long eligible = value.getPickupEligibleCount() == null ? 0 : value.getPickupEligibleCount();
        long picked = value.getPickedUpCount() == null ? 0 : value.getPickedUpCount();
        BigDecimal rate = eligible == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(picked).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
        return new DashboardMetrics(value.getUserCount(), value.getProductCount(), value.getActivityCount(),
                value.getReservationCount(), value.getOrderCount(), value.getPaidAmount(), eligible, picked, rate);
    }
}
