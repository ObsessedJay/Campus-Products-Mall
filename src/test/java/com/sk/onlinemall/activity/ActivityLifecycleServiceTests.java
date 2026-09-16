package com.sk.onlinemall.activity;

import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.service.ActivityLifecycleService;
import com.sk.onlinemall.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ActivityLifecycleServiceTests {
    @Autowired
    private ActivityLifecycleService lifecycleService;

    /**
     * 验证发布、开售和结束的合法状态转换。
     */
    @Test
    void shouldTransitionThroughPublishedLifecycle() {
        ActivityStatus reserving = lifecycleService.transition(
                ActivityStatus.UNPUBLISHED, ActivityEvent.PUBLISH_WITH_RESERVATION);
        ActivityStatus pending = lifecycleService.transition(reserving, ActivityEvent.CLOSE_RESERVATION);
        ActivityStatus running = lifecycleService.transition(pending, ActivityEvent.START);
        ActivityStatus ended = lifecycleService.transition(running, ActivityEvent.FINISH);

        assertThat(reserving).isEqualTo(ActivityStatus.RESERVING);
        assertThat(pending).isEqualTo(ActivityStatus.PENDING);
        assertThat(running).isEqualTo(ActivityStatus.RUNNING);
        assertThat(ended).isEqualTo(ActivityStatus.ENDED);
    }

    /**
     * 验证结束状态不能重新发布或终止。
     */
    @Test
    void shouldRejectIllegalTransitionFromEndedActivity() {
        assertThatThrownBy(() -> lifecycleService.transition(
                ActivityStatus.ENDED, ActivityEvent.PUBLISH_DIRECT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> lifecycleService.transition(
                ActivityStatus.ENDED, ActivityEvent.TERMINATE))
                .isInstanceOf(BusinessException.class);
    }
}
