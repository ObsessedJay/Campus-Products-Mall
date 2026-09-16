package com.sk.onlinemall.messaging;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.order.dto.CreateOrderRequest;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestStatus;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.order.service.FlashSaleRequestService;
import com.sk.onlinemall.order.service.TradeOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.flash-sale.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOrderConsumer {
    private static final int MAX_ATTEMPTS = 3;

    private final TradeOrderService orderService;
    private final FlashSaleRequestService requestService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 创建 FlashSaleOrderConsumer 实例。
     *
     * @param orderService 订单业务服务
     * @param requestService 抢购请求业务服务
     * @param rabbitTemplate RabbitMQ 消息发送组件
     */
    public FlashSaleOrderConsumer(TradeOrderService orderService, FlashSaleRequestService requestService,
                                  RabbitTemplate rabbitTemplate) {
        this.orderService = orderService;
        this.requestService = requestService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 消费抢购消息并创建订单。
     *
     * @param message 抢购订单消息
     */
    @RabbitListener(queues = RabbitTopology.ORDER_QUEUE, concurrency = "${app.flash-sale.consumer-concurrency:2}")
    public void createOrder(FlashSaleOrderMessage message) {
        FlashSaleRequestEntity request = requestService.findForConsumer(message.requestNo());
        if (request == null || request.getStatus() != FlashSaleRequestStatus.PENDING) return;
        try {
            TradeOrderEntity order = orderService.create(
                    new CreateOrderRequest(message.requestNo(), message.productId(), message.quantity(),
                            message.activityId(), message.skuId()),
                    message.username());
            requestService.complete(message, order.getId());
        } catch (BusinessException exception) {
            requestService.fail(message, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            retryOrDeadLetter(message, exception);
        }
    }

    /**
     * 根据重试次数重新投递消息或转入死信队列。
     *
     * @param message 抢购订单消息
     * @param exception 捕获的异常
     */
    private void retryOrDeadLetter(FlashSaleOrderMessage message, RuntimeException exception) {
        if (message.attempt() + 1 < MAX_ATTEMPTS) {
            try {
                rabbitTemplate.convertAndSend(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.ORDER_ROUTING_KEY,
                        message.nextAttempt());
            } catch (RuntimeException publishException) {
                requestService.fail(message, "FLASH_SALE_QUEUE_UNAVAILABLE",
                        "order retry queue is temporarily unavailable");
                throw publishException;
            }
            return;
        }
        requestService.fail(message, "FLASH_SALE_ORDER_FAILED", "asynchronous order creation failed");
        rabbitTemplate.convertAndSend(RabbitTopology.DEAD_LETTER_EXCHANGE,
                RabbitTopology.DEAD_LETTER_ROUTING_KEY, message.nextAttempt());
    }
}
