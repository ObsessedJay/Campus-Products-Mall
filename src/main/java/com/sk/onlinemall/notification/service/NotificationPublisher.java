package com.sk.onlinemall.notification.service;

import com.sk.onlinemall.common.util.TransactionCallbackUtil;
import com.sk.onlinemall.messaging.RabbitTopology;
import com.sk.onlinemall.notification.model.NotificationMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    /**
     * 创建异步通知发布器。
     *
     * @param rabbitTemplate RabbitMQ 操作组件
     * @param enabled 是否启用异步通知发布
     */
    public NotificationPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${app.notification.enabled:true}") boolean enabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
    }

    /**
     * 在当前事务提交后发布通知消息。
     *
     * @param message 通知消息
     */
    public void publishAfterCommit(NotificationMessage message) {
        if (!enabled) return;
        Runnable action = () -> rabbitTemplate.convertAndSend(
                RabbitTopology.NOTIFICATION_EXCHANGE,
                RabbitTopology.NOTIFICATION_ROUTING_KEY,
                message);
        TransactionCallbackUtil.afterCommit(action);
    }
}
