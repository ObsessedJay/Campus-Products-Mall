package com.sk.onlinemall.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitConfig {

    /**
     * 创建 RabbitMQ JSON 消息转换器。
     *
     * @return 方法执行结果
     */
    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 创建抢购订单交换机。
     *
     * @return 方法执行结果
     */
    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(RabbitTopology.ORDER_EXCHANGE, true, false);
    }

    /**
     * 创建抢购订单死信交换机。
     *
     * @return 方法执行结果
     */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * 创建站内通知交换机。
     *
     * @return 通知交换机
     */
    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(RabbitTopology.NOTIFICATION_EXCHANGE, true, false);
    }

    /**
     * 创建抢购订单队列。
     *
     * @return 方法执行结果
     */
    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(RabbitTopology.ORDER_QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * 创建抢购订单死信队列。
     *
     * @return 方法执行结果
     */
    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    /**
     * 创建站内通知队列。
     *
     * @return 通知队列
     */
    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(RabbitTopology.NOTIFICATION_QUEUE).build();
    }

    /**
     * 绑定抢购订单队列和交换机。
     *
     * @param orderQueue 订单队列参数
     * @param orderExchange 订单Exchange参数
     * @return 方法执行结果
     */
    @Bean
    Binding orderBinding(@Qualifier("orderQueue") Queue orderQueue,
                         @Qualifier("orderExchange") DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(RabbitTopology.ORDER_ROUTING_KEY);
    }

    /**
     * 绑定死信队列和死信交换机。
     *
     * @param deadLetterQueue 死信Letter队列参数
     * @param deadLetterExchange 死信LetterExchange参数
     * @return 方法执行结果
     */
    @Bean
    Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue,
                              @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }


    /**
     * 绑定站内通知队列与交换机。
     *
     * @param notificationQueue 通知队列
     * @param notificationExchange 通知交换机
     * @return 通知绑定
     */
    @Bean
    Binding notificationBinding(@Qualifier("notificationQueue") Queue notificationQueue,
                                @Qualifier("notificationExchange") DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with(RabbitTopology.NOTIFICATION_ROUTING_KEY);
    }
}
