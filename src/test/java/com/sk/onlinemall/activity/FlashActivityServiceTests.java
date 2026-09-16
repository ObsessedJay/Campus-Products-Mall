package com.sk.onlinemall.activity;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.service.FlashActivityService;
import com.sk.onlinemall.activity.service.ActivityLifecycleService;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.realtime.FlashSaleStatusWebSocketHandler;
import com.sk.onlinemall.notification.service.NotificationPublisher;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashActivityServiceTests {

    /**
     * 验证活动状态会随预约和发售时间正确流转。
     */
    @Test
    void shouldSynchronizeReservationStartAndEndTransitions() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 21, 18, 0);
        FlashActivityEntity reservationEnded = activity(101L, ActivityStatus.RESERVING,
                now.plusMinutes(30), now.plusHours(1), now.minusMinutes(5));
        FlashActivityEntity started = activity(102L, ActivityStatus.PENDING,
                now.minusSeconds(1), now.plusHours(1), null);
        FlashActivityEntity ended = activity(103L, ActivityStatus.RUNNING,
                now.minusHours(1), now.minusSeconds(1), null);

        FlashActivityMapper mapper = mock(FlashActivityMapper.class);
        FlashSaleStatusWebSocketHandler publisher = mock(FlashSaleStatusWebSocketHandler.class);
        ActivityLifecycleService lifecycle = mock(ActivityLifecycleService.class);
        when(mapper.findNeedingStateSync(now)).thenReturn(List.of(reservationEnded, started, ended));
        when(mapper.updateStatusIfCurrent(101L, ActivityStatus.PENDING, ActivityStatus.RESERVING)).thenReturn(1);
        when(mapper.updateStatusIfCurrent(102L, ActivityStatus.RUNNING, ActivityStatus.PENDING)).thenReturn(1);
        when(mapper.updateStatusIfCurrent(103L, ActivityStatus.ENDED, ActivityStatus.RUNNING)).thenReturn(1);
        when(lifecycle.transition(ActivityStatus.RESERVING, ActivityEvent.CLOSE_RESERVATION))
                .thenReturn(ActivityStatus.PENDING);
        when(lifecycle.transition(ActivityStatus.PENDING, ActivityEvent.START)).thenReturn(ActivityStatus.RUNNING);
        when(lifecycle.transition(ActivityStatus.RUNNING, ActivityEvent.FINISH)).thenReturn(ActivityStatus.ENDED);

        int changed = service(mapper, publisher, lifecycle).synchronizeActivityStates(now);

        assertThat(changed).isEqualTo(3);
        assertThat(reservationEnded.getStatus()).isEqualTo(ActivityStatus.PENDING);
        assertThat(started.getStatus()).isEqualTo(ActivityStatus.RUNNING);
        assertThat(ended.getStatus()).isEqualTo(ActivityStatus.ENDED);
        verify(publisher).publishActivityAfterCommit(reservationEnded);
        verify(publisher).publishActivityAfterCommit(started);
        verify(publisher).publishActivityAfterCommit(ended);
    }

    /**
     * 验证并发状态更新失败时不会重复发布活动。
     */
    @Test
    void shouldNotPublishWhenConditionalStateUpdateLosesRace() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 21, 18, 0);
        FlashActivityEntity activity = activity(104L, ActivityStatus.PENDING,
                now.minusSeconds(1), now.plusHours(1), null);
        FlashActivityMapper mapper = mock(FlashActivityMapper.class);
        FlashSaleStatusWebSocketHandler publisher = mock(FlashSaleStatusWebSocketHandler.class);
        ActivityLifecycleService lifecycle = mock(ActivityLifecycleService.class);
        when(mapper.findNeedingStateSync(now)).thenReturn(List.of(activity));
        when(mapper.updateStatusIfCurrent(104L, ActivityStatus.RUNNING, ActivityStatus.PENDING)).thenReturn(0);
        when(lifecycle.transition(ActivityStatus.PENDING, ActivityEvent.START)).thenReturn(ActivityStatus.RUNNING);

        int changed = service(mapper, publisher, lifecycle).synchronizeActivityStates(now);

        assertThat(changed).isZero();
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.PENDING);
        verify(publisher, never()).publishActivityAfterCommit(activity);
    }

    /**
     * 创建待测活动服务并注入模拟依赖。
     *
     * @param mapper 数据访问组件
     * @param publisher 实时状态发布组件
     * @param lifecycle 活动生命周期服务
     * @return 方法执行结果
     */
    private FlashActivityService service(FlashActivityMapper mapper,
                                         FlashSaleStatusWebSocketHandler publisher,
                                         ActivityLifecycleService lifecycle) {
        return new FlashActivityService(
                mapper, mock(ProductMapper.class), mock(UserMapper.class), mock(PickupPointMapper.class),
                publisher, lifecycle, mock(NotificationPublisher.class));
    }

    /**
     * 创建指定状态和时间范围的测试活动。
     *
     * @param id 记录主键
     * @param status 业务状态或连接关闭状态
     * @param startAt 活动开始时间
     * @param endAt 活动结束时间
     * @param reservationEndAt 预约EndAt参数
     * @return 方法执行结果
     */
    private FlashActivityEntity activity(Long id, ActivityStatus status, LocalDateTime startAt,
                                         LocalDateTime endAt, LocalDateTime reservationEndAt) {
        FlashActivityEntity activity = new FlashActivityEntity();
        activity.setId(id);
        activity.setStatus(status);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setReservationEndAt(reservationEndAt);
        return activity;
    }
}
