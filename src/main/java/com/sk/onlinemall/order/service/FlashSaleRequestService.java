package com.sk.onlinemall.order.service;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.ActivityMode;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.ReservationStatus;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.messaging.FlashSaleOrderMessage;
import com.sk.onlinemall.messaging.RabbitTopology;
import com.sk.onlinemall.order.dto.CreateOrderRequest;
import com.sk.onlinemall.order.mapper.FlashSaleRequestMapper;
import com.sk.onlinemall.order.mapper.FlashSaleCompensationMapper;
import com.sk.onlinemall.order.mapper.TradeOrderMapper;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.model.FlashSaleCompensationEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestStatus;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.mapper.ProductSkuMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductSkuEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.realtime.FlashSaleStatusWebSocketHandler;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashSaleRequestService {
    private final FlashSaleRequestMapper requestMapper;
    private final FlashSaleCompensationMapper compensationMapper;
    private final FlashActivityMapper activityMapper;
    private final TradeOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final UserMapper userMapper;
    private final FlashSaleInventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;
    private final FlashSaleStatusWebSocketHandler statusPublisher;

    /**
     * 创建 FlashSaleRequestService 实例。
     *
     * @param requestMapper 请求数据访问组件
     * @param compensationMapper 补偿数据访问组件
     * @param activityMapper 活动数据访问组件
     * @param orderMapper 订单数据访问组件
     * @param productMapper 商品数据访问组件
     * @param skuMapper 商品规格数据访问组件
     * @param userMapper 用户数据访问组件
     * @param inventoryService 库存业务服务
     * @param rabbitTemplate RabbitMQ 消息发送组件
     * @param statusPublisher 实时状态发布组件
     */
    public FlashSaleRequestService(FlashSaleRequestMapper requestMapper,
                                   FlashSaleCompensationMapper compensationMapper,
                                   FlashActivityMapper activityMapper,
                                   TradeOrderMapper orderMapper, ProductMapper productMapper,
                                   ProductSkuMapper skuMapper, UserMapper userMapper,
                                   FlashSaleInventoryService inventoryService, RabbitTemplate rabbitTemplate,
                                   FlashSaleStatusWebSocketHandler statusPublisher) {
        this.requestMapper = requestMapper;
        this.compensationMapper = compensationMapper;
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.userMapper = userMapper;
        this.inventoryService = inventoryService;
        this.rabbitTemplate = rabbitTemplate;
        this.statusPublisher = statusPublisher;
    }

    /**
     * 受理抢购请求并投递异步下单消息。
     *
     * @param request 请求参数
     * @param username 当前用户名
     * @return 方法执行结果
     */
    public FlashSaleRequestEntity accept(CreateOrderRequest request, String username) {
        if (request.activityId() == null) {
            throw new BusinessException("ACTIVITY_REQUIRED", "activityId is required for flash-sale requests");
        }
        String requestNo = request.requestNo().trim();
        UserEntity user = requireStudent(username);
        FlashSaleRequestEntity existing = requestMapper.findByRequestNo(requestNo);
        if (existing != null) return requireOwned(existing, user.getId());

        ProductEntity product = productMapper.findById(request.productId());
        FlashActivityEntity activity = validate(request, user, product);
        int purchased = orderMapper.sumActiveQuantityByActivityAndUser(activity.getId(), user.getId());

        FlashSaleRequestEntity accepted = new FlashSaleRequestEntity();
        accepted.setRequestNo(requestNo);
        accepted.setActivityId(activity.getId());
        accepted.setProductId(product.getId());
        accepted.setSkuId(request.skuId());
        accepted.setUserId(user.getId());
        accepted.setQuantity(request.quantity());
        accepted.setStatus(FlashSaleRequestStatus.ACCEPTING);
        try {
            requestMapper.insert(accepted);
        } catch (DuplicateKeyException exception) {
            return requireOwned(requestMapper.findByRequestNo(requestNo), user.getId());
        }

        boolean reserved = false;
        try {
            inventoryService.reserve(activity, user.getId(), request.quantity(), purchased, requestNo);
            reserved = true;
            if (requestMapper.markPending(requestNo) != 1) {
                throw new BusinessException("FLASH_SALE_REQUEST_STATE_CHANGED",
                        "flash-sale request state changed before queueing");
            }
            rabbitTemplate.convertAndSend(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.ORDER_ROUTING_KEY,
                    new FlashSaleOrderMessage(requestNo, activity.getId(), product.getId(), user.getId(),
                            user.getUsername(), request.quantity(), 0, request.skuId()));
            FlashSaleRequestEntity pending = requestMapper.findByRequestNo(requestNo);
            statusPublisher.publish(pending);
            return pending;
        } catch (BusinessException exception) {
            requestMapper.markFailed(requestNo, exception.getCode(), TextUtil.truncate(exception.getMessage(), 500));
            if (reserved) inventoryService.release(activity.getId(), user.getId(), request.quantity(), requestNo, "FAILED");
            publishCurrent(requestNo);
            throw exception;
        } catch (RuntimeException exception) {
            requestMapper.markFailed(requestNo, "FLASH_SALE_QUEUE_UNAVAILABLE", "order queue is temporarily unavailable");
            if (reserved) inventoryService.release(activity.getId(), user.getId(), request.quantity(), requestNo, "FAILED");
            publishCurrent(requestNo);
            throw new BusinessException("FLASH_SALE_QUEUE_UNAVAILABLE", "order queue is temporarily unavailable");
        }
    }

    /**
     * 查询我的数据。
     *
     * @param requestNo 业务请求号
     * @param username 当前用户名
     * @return 查询结果
     */
    public FlashSaleRequestEntity findMine(String requestNo, String username) {
        UserEntity user = requireStudent(username);
        FlashSaleRequestEntity request = requestMapper.findByRequestNo(requestNo.trim());
        if (request == null) throw new BusinessException("FLASH_SALE_REQUEST_NOT_FOUND", "flash-sale request does not exist");
        return requireOwned(request, user.getId());
    }

    /**
     * 查询并校验待消费的抢购请求。
     *
     * @param requestNo 业务请求号
     * @return 查询结果
     */
    public FlashSaleRequestEntity findForConsumer(String requestNo) {
        return requestMapper.findByRequestNo(requestNo);
    }

    /**
     * 查询失败的。
     *
     * @param activityId 活动主键
     * @param failureCode 失败原因编码
     * @param username 当前用户名
     * @return 查询结果
     */
    public List<FlashSaleRequestEntity> findFailed(Long activityId, String failureCode, String username) {
        requireOperator(username);
        String normalizedCode = TextUtil.trimToNull(failureCode);
        return requestMapper.findFailed(activityId, normalizedCode);
    }

    /**
     * 查询补偿记录。
     *
     * @param requestNo 业务请求号
     * @param username 当前用户名
     * @return 查询结果
     */
    public List<FlashSaleCompensationEntity> findCompensations(String requestNo, String username) {
        requireOperator(username);
        FlashSaleRequestEntity request = requestMapper.findByRequestNo(requestNo.trim());
        if (request == null) {
            throw new BusinessException("FLASH_SALE_REQUEST_NOT_FOUND", "flash-sale request does not exist");
        }
        return compensationMapper.findByRequestNo(request.getRequestNo());
    }

    /**
     * 对失败的抢购请求执行人工补偿。
     *
     * @param requestNo 业务请求号
     * @param reason 操作原因
     * @param username 当前用户名
     * @return 方法执行结果
     */
    public FlashSaleCompensationEntity compensate(String requestNo, String reason, String username) {
        UserEntity operator = requireOperator(username);
        String normalizedRequestNo = requestNo.trim();
        FlashSaleRequestEntity failed = requestMapper.findByRequestNo(normalizedRequestNo);
        if (failed == null) {
            throw new BusinessException("FLASH_SALE_REQUEST_NOT_FOUND", "flash-sale request does not exist");
        }
        if (requestMapper.markCompensating(normalizedRequestNo) != 1) {
            throw new BusinessException("FLASH_SALE_COMPENSATION_STATE_CHANGED",
                    "request is no longer available for compensation");
        }
        publishCurrent(normalizedRequestNo);

        FlashSaleCompensationEntity audit = new FlashSaleCompensationEntity();
        audit.setRequestId(failed.getId());
        audit.setRequestNo(failed.getRequestNo());
        audit.setOperatedBy(operator.getId());
        audit.setReason(reason.trim());
        audit.setPreviousFailureCode(failed.getFailureCode());
        audit.setPreviousFailureMessage(failed.getFailureMessage());
        audit.setStatus("PENDING");
        compensationMapper.insert(audit);

        boolean reserved = false;
        try {
            UserEntity student = userMapper.findById(failed.getUserId());
            if (student == null || student.getStatus() != UserStatus.ACTIVE || student.getRole() != UserRole.STUDENT) {
                throw new BusinessException("STUDENT_NOT_AVAILABLE", "active student account is required");
            }
            ProductEntity product = productMapper.findById(failed.getProductId());
            CreateOrderRequest original = new CreateOrderRequest(failed.getRequestNo(), failed.getProductId(),
                    failed.getQuantity(), failed.getActivityId(), failed.getSkuId());
            FlashActivityEntity activity = validate(original, student, product);
            int purchased = orderMapper.sumActiveQuantityByActivityAndUser(activity.getId(), student.getId());
            inventoryService.reserve(activity, student.getId(), failed.getQuantity(), purchased, failed.getRequestNo());
            reserved = true;
            if (requestMapper.markPendingFromCompensation(failed.getRequestNo()) != 1) {
                throw new BusinessException("FLASH_SALE_COMPENSATION_STATE_CHANGED",
                        "request state changed before compensation could be queued");
            }
            rabbitTemplate.convertAndSend(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.ORDER_ROUTING_KEY,
                    new FlashSaleOrderMessage(failed.getRequestNo(), activity.getId(), product.getId(), student.getId(),
                            student.getUsername(), failed.getQuantity(), 0, failed.getSkuId()));
            publishCurrent(failed.getRequestNo());
            return compensationMapper.findById(audit.getId());
        } catch (BusinessException exception) {
            failCompensation(failed, audit, exception.getCode(), exception.getMessage(), reserved);
            throw exception;
        } catch (RuntimeException exception) {
            failCompensation(failed, audit, "FLASH_SALE_QUEUE_UNAVAILABLE",
                    "order queue is temporarily unavailable", reserved);
            throw new BusinessException("FLASH_SALE_QUEUE_UNAVAILABLE", "order queue is temporarily unavailable");
        }
    }

    /**
     * 完成当前业务处理并更新最终状态。
     *
     * @param message 抢购订单消息
     * @param orderId 订单主键
     */
    public void complete(FlashSaleOrderMessage message, long orderId) {
        if (requestMapper.markSucceeded(message.requestNo(), orderId) == 1) {
            inventoryService.markSucceeded(message.requestNo());
            completeCompensation(message.requestNo(), "SUCCEEDED", null);
            publishCurrent(message.requestNo());
        }
    }

    /**
     * 记录抢购消费失败并释放预扣库存。
     *
     * @param message 抢购订单消息
     * @param code 验证码或业务编码
     * @param failureMessage 失败原因说明
     */
    public void fail(FlashSaleOrderMessage message, String code, String failureMessage) {
        if (requestMapper.markFailed(message.requestNo(), code, TextUtil.truncate(failureMessage, 500)) == 1) {
            inventoryService.release(message.activityId(), message.userId(), message.quantity(),
                    message.requestNo(), "FAILED");
            completeCompensation(message.requestNo(), "FAILED", TextUtil.truncate(failureMessage, 500));
            publishCurrent(message.requestNo());
        }
    }

    /**
     * 记录抢购补偿失败并回滚预扣库存。
     *
     * @param request 请求参数
     * @param audit 补偿审计记录
     * @param code 验证码或业务编码
     * @param message 抢购订单消息
     * @param reserved 是否已经完成库存预扣
     */
    private void failCompensation(FlashSaleRequestEntity request, FlashSaleCompensationEntity audit,
                                  String code, String message, boolean reserved) {
        String failureMessage = TextUtil.truncate(message, 500);
        requestMapper.markCompensationFailed(request.getRequestNo(), code, failureMessage);
        if (reserved) {
            inventoryService.release(request.getActivityId(), request.getUserId(), request.getQuantity(),
                    request.getRequestNo(), "FAILED");
        }
        completeCompensation(audit.getRequestNo(), "FAILED", failureMessage);
        publishCurrent(request.getRequestNo());
    }

    /**
     * 完成最近一条待处理补偿审计记录。
     *
     * @param requestNo 业务请求号
     * @param status 业务状态或连接关闭状态
     * @param failureMessage 失败原因说明
     */
    private void completeCompensation(String requestNo, String status, String failureMessage) {
        Long compensationId = compensationMapper.findLatestPendingId(requestNo);
        if (compensationId != null) {
            compensationMapper.complete(compensationId, status, failureMessage);
        }
    }

    /**
     * 发布当前。
     *
     * @param requestNo 业务请求号
     */
    private void publishCurrent(String requestNo) {
        statusPublisher.publish(requestMapper.findByRequestNo(requestNo));
    }

    /**
     * 校验抢购活动、用户资格和商品状态。
     *
     * @param request 请求参数
     * @param user 用户信息
     * @param product 商品信息
     * @return 方法执行结果
     */
    private FlashActivityEntity validate(CreateOrderRequest request, UserEntity user, ProductEntity product) {
        if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_AVAILABLE", "product is not available for ordering");
        }
        if (request.quantity() > product.getLimitPerUser()) {
            throw new BusinessException("LIMIT_EXCEEDED", "quantity exceeds product purchase limit");
        }
        validateSku(request, product);
        FlashActivityEntity activity = activityMapper.findById(request.activityId());
        if (activity == null || !activity.getProductId().equals(product.getId())) {
            throw new BusinessException("ACTIVITY_PRODUCT_MISMATCH", "activity does not match the product");
        }
        if (activity.getStatus() == ActivityStatus.TERMINATED || activity.getStatus() == ActivityStatus.ENDED
                || activity.getStatus() == ActivityStatus.UNPUBLISHED
                || activity.getStatus() == ActivityStatus.PENDING_REVIEW
                || activity.getStatus() == ActivityStatus.REJECTED) {
            throw new BusinessException("ACTIVITY_NOT_AVAILABLE", "activity is not available for ordering");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartAt() == null || activity.getEndAt() == null || now.isBefore(activity.getStartAt())) {
            throw new BusinessException("ACTIVITY_NOT_STARTED", "activity has not started");
        }
        if (!now.isBefore(activity.getEndAt())) {
            throw new BusinessException("ACTIVITY_ENDED", "activity has ended");
        }
        int purchased = orderMapper.sumActiveQuantityByActivityAndUser(activity.getId(), user.getId());
        if (request.quantity() > activity.getLimitPerUser()
                || purchased + request.quantity() > activity.getLimitPerUser()) {
            throw new BusinessException("ACTIVITY_LIMIT_EXCEEDED", "activity purchase limit has been reached");
        }
        if (activity.getMode() == ActivityMode.LOTTERY) {
            var reservation = activityMapper.findReservation(activity.getId(), user.getId());
            if (reservation == null || reservation.getStatus() != ReservationStatus.QUALIFIED) {
                throw new BusinessException("ACTIVITY_QUALIFICATION_REQUIRED", "lottery qualification is required");
            }
        }
        return activity;
    }

    /**
     * 校验抢购请求的规格归属与可用库存。
     *
     * @param request 下单请求
     * @param product 商品信息
     */
    private void validateSku(CreateOrderRequest request, ProductEntity product) {
        int skuCount = skuMapper.countByProductId(product.getId());
        if (skuCount == 0) {
            if (request.skuId() != null) {
                throw new BusinessException("SKU_NOT_FOUND", "product sku does not exist");
            }
            return;
        }
        if (request.skuId() == null) {
            throw new BusinessException("SKU_REQUIRED", "skuId is required for this product");
        }
        ProductSkuEntity sku = skuMapper.findById(request.skuId());
        if (sku == null || !sku.getProductId().equals(product.getId()) || !Boolean.TRUE.equals(sku.getEnabled())) {
            throw new BusinessException("SKU_NOT_AVAILABLE", "product sku is not available");
        }
        if (sku.getStock() < request.quantity()) {
            throw new BusinessException("SKU_STOCK_INSUFFICIENT", "product sku stock is insufficient");
        }
    }

    /**
     * 查询并校验学生。
     *
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private UserEntity requireStudent(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getRole() != UserRole.STUDENT) {
            throw new BusinessException("STUDENT_NOT_AVAILABLE", "active student account is required");
        }
        return user;
    }

    /**
     * 查询并校验运营操作人。
     *
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private UserEntity requireOperator(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || (!user.getRole().isMerchant() && user.getRole() != UserRole.ADMIN)) {
            throw new BusinessException("OPERATOR_NOT_AVAILABLE", "active operator account is required");
        }
        return user;
    }

    /**
     * 查询并校验归属当前用户的。
     *
     * @param request 请求参数
     * @param userId 用户主键
     * @return 方法执行结果
     */
    private FlashSaleRequestEntity requireOwned(FlashSaleRequestEntity request, Long userId) {
        if (request == null || !request.getUserId().equals(userId)) {
            throw new BusinessException("FLASH_SALE_REQUEST_NOT_FOUND", "flash-sale request does not exist");
        }
        return request;
    }

}
