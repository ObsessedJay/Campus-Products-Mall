package com.sk.onlinemall.messaging;

public final class RabbitTopology {
    public static final String ORDER_EXCHANGE = "campus.flash-sale.order.exchange";
    public static final String ORDER_QUEUE = "campus.flash-sale.order.queue";
    public static final String ORDER_ROUTING_KEY = "order.create";
    public static final String DEAD_LETTER_EXCHANGE = "campus.flash-sale.dead-letter.exchange";
    public static final String DEAD_LETTER_QUEUE = "campus.flash-sale.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "order.dead";
    public static final String NOTIFICATION_EXCHANGE = "campus.notification.exchange";
    public static final String NOTIFICATION_QUEUE = "campus.notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.create";

    /**
     * 创建 RabbitTopology 实例。
     */
    private RabbitTopology() {
    }
}
