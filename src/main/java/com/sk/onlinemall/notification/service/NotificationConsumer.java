package com.sk.onlinemall.notification.service;

import com.sk.onlinemall.messaging.RabbitTopology;
import com.sk.onlinemall.notification.mapper.NotificationMapper;
import com.sk.onlinemall.notification.model.NoticeEntity;
import com.sk.onlinemall.notification.model.NotificationMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationConsumer {
    private final NotificationMapper notificationMapper;

    /**
     * 创建通知消息消费者。
     *
     * @param notificationMapper 通知数据访问组件
     */
    public NotificationConsumer(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 幂等保存站内通知与用户消息关系。
     *
     * @param message 通知消息
     */
    @Transactional
    @RabbitListener(queues = RabbitTopology.NOTIFICATION_QUEUE)
    public void consume(NotificationMessage message) {
        NoticeEntity notice = notificationMapper.findNoticeByBusinessKey(message.businessKey());
        if (notice == null) {
            notice = new NoticeEntity();
            notice.setNoticeType(message.type());
            notice.setBusinessKey(message.businessKey());
            notice.setTitle(message.title());
            notice.setContent(message.content());
            notice.setLink(message.link());
            try {
                notificationMapper.insertNotice(notice);
            } catch (DuplicateKeyException exception) {
                notice = notificationMapper.findNoticeByBusinessKey(message.businessKey());
            }
        }
        try {
            notificationMapper.insertUserMessage(notice.getId(), message.userId());
        } catch (DuplicateKeyException ignored) {
            // RabbitMQ 至少一次投递时，唯一键将重复消息收敛为同一条用户消息。
        }
    }
}
