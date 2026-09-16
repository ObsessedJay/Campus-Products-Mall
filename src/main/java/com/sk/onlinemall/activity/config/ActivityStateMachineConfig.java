package com.sk.onlinemall.activity.config;

import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.ActivityStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory(name = "activityStateMachineFactory")
public class ActivityStateMachineConfig
        extends EnumStateMachineConfigurerAdapter<ActivityStatus, ActivityEvent> {

    /**
     * 注册活动状态集合和默认初始状态。
     *
     * @param states 状态配置器
     * @throws Exception 状态配置失败时抛出
     */
    @Override
    public void configure(StateMachineStateConfigurer<ActivityStatus, ActivityEvent> states) throws Exception {
        states.withStates().initial(ActivityStatus.UNPUBLISHED).states(EnumSet.allOf(ActivityStatus.class));
    }

    /**
     * 集中声明活动生命周期允许的状态转换。
     *
     * @param transitions 转换配置器
     * @throws Exception 转换配置失败时抛出
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<ActivityStatus, ActivityEvent> transitions)
            throws Exception {
        transitions
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.UNPUBLISHED)
                    .event(ActivityEvent.EDIT).and()
                .withExternal().source(ActivityStatus.PENDING_REVIEW).target(ActivityStatus.UNPUBLISHED)
                    .event(ActivityEvent.EDIT).and()
                .withExternal().source(ActivityStatus.REJECTED).target(ActivityStatus.UNPUBLISHED)
                    .event(ActivityEvent.EDIT).and()
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.UNPUBLISHED)
                    .event(ActivityEvent.REVIEW_APPROVE).and()
                .withExternal().source(ActivityStatus.PENDING_REVIEW).target(ActivityStatus.UNPUBLISHED)
                    .event(ActivityEvent.REVIEW_APPROVE).and()
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.REJECTED)
                    .event(ActivityEvent.REVIEW_REJECT).and()
                .withExternal().source(ActivityStatus.PENDING_REVIEW).target(ActivityStatus.REJECTED)
                    .event(ActivityEvent.REVIEW_REJECT).and()
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.RESERVING)
                    .event(ActivityEvent.PUBLISH_WITH_RESERVATION).and()
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.PENDING)
                    .event(ActivityEvent.PUBLISH_DIRECT).and()
                .withExternal().source(ActivityStatus.RESERVING).target(ActivityStatus.PENDING)
                    .event(ActivityEvent.CLOSE_RESERVATION).and()
                .withExternal().source(ActivityStatus.RESERVING).target(ActivityStatus.RUNNING)
                    .event(ActivityEvent.START).and()
                .withExternal().source(ActivityStatus.PENDING).target(ActivityStatus.RUNNING)
                    .event(ActivityEvent.START).and()
                .withExternal().source(ActivityStatus.RESERVING).target(ActivityStatus.ENDED)
                    .event(ActivityEvent.FINISH).and()
                .withExternal().source(ActivityStatus.PENDING).target(ActivityStatus.ENDED)
                    .event(ActivityEvent.FINISH).and()
                .withExternal().source(ActivityStatus.RUNNING).target(ActivityStatus.ENDED)
                    .event(ActivityEvent.FINISH).and()
                .withExternal().source(ActivityStatus.UNPUBLISHED).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE).and()
                .withExternal().source(ActivityStatus.PENDING_REVIEW).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE).and()
                .withExternal().source(ActivityStatus.REJECTED).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE).and()
                .withExternal().source(ActivityStatus.RESERVING).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE).and()
                .withExternal().source(ActivityStatus.PENDING).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE).and()
                .withExternal().source(ActivityStatus.RUNNING).target(ActivityStatus.TERMINATED)
                    .event(ActivityEvent.TERMINATE);
    }
}
