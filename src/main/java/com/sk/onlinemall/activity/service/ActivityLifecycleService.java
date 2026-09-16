package com.sk.onlinemall.activity.service;

import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ActivityLifecycleService {
    private final StateMachineFactory<ActivityStatus, ActivityEvent> stateMachineFactory;

    /**
     * 创建活动生命周期服务。
     *
     * @param stateMachineFactory 活动状态机工厂
     */
    public ActivityLifecycleService(
            @Qualifier("activityStateMachineFactory")
            StateMachineFactory<ActivityStatus, ActivityEvent> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
    }

    /**
     * 执行活动状态事件并返回目标状态。
     *
     * @param currentStatus 当前状态
     * @param event 生命周期事件
     * @return 目标状态
     */
    public ActivityStatus transition(ActivityStatus currentStatus, ActivityEvent event) {
        StateMachine<ActivityStatus, ActivityEvent> machine =
                stateMachineFactory.getStateMachine(UUID.randomUUID());
        machine.stopReactively().block();
        machine.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(currentStatus, null, null, null)).block());
        machine.startReactively().block();
        StateMachineEventResult<ActivityStatus, ActivityEvent> result = machine
                .sendEvent(Mono.just(MessageBuilder.withPayload(event).build()))
                .blockLast();
        ActivityStatus targetStatus = machine.getState().getId();
        machine.stopReactively().block();
        if (result == null || result.getResultType() != StateMachineEventResult.ResultType.ACCEPTED) {
            throw new BusinessException("ACTIVITY_TRANSITION_NOT_ALLOWED",
                    "activity event is not allowed in its current state");
        }
        return targetStatus;
    }
}
