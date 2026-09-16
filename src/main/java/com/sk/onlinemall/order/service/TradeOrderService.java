package com.sk.onlinemall.order.service;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.ActivityMode;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.ReservationStatus;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.IdentifierUtil;
import com.sk.onlinemall.order.dto.CreateOrderRequest;
import com.sk.onlinemall.order.dto.PayOrderRequest;
import com.sk.onlinemall.order.mapper.TradeOrderMapper;
import com.sk.onlinemall.order.model.OrderDetailResponse;
import com.sk.onlinemall.order.model.OrderStatus;
import com.sk.onlinemall.order.model.PaymentRecordEntity;
import com.sk.onlinemall.order.model.PaymentStatus;
import com.sk.onlinemall.order.model.PickupVerificationEntity;
import com.sk.onlinemall.order.model.RefundRecordEntity;
import com.sk.onlinemall.order.model.RefundStatus;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.order.model.TradeOrderItemEntity;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.mapper.ProductSkuMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductSkuEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.pickup.mapper.OperatorPickupPointMapper;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointEntity;
import com.sk.onlinemall.pickup.model.PickupPointStatus;
import com.sk.onlinemall.realtime.FlashSaleStatusWebSocketHandler;
import com.sk.onlinemall.notification.model.NotificationMessage;
import com.sk.onlinemall.notification.model.NotificationType;
import com.sk.onlinemall.notification.service.NotificationPublisher;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserStatus;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.service.SystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TradeOrderService {
    private final TradeOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final UserMapper userMapper;
    private final FlashActivityMapper activityMapper;
    private final PickupPointMapper pickupPointMapper;
    private final OperatorPickupPointMapper operatorPickupPointMapper;
    private final FlashSaleInventoryService inventoryService;
    private final FlashSaleStatusWebSocketHandler statusPublisher;
    private final NotificationPublisher notificationPublisher;
    private final SystemConfigService systemConfigService;

    /**
     * 创建 TradeOrderService 实例。
     *
     * @param orderMapper 订单数据访问组件
     * @param productMapper 商品数据访问组件
     * @param skuMapper 商品规格数据访问组件
     * @param userMapper 用户数据访问组件
     * @param activityMapper 活动数据访问组件
     * @param pickupPointMapper 自提点数据访问组件
     * @param operatorPickupPointMapper 运营点位授权数据访问组件
     * @param inventoryService 库存业务服务
     * @param statusPublisher 实时状态发布组件
     * @param notificationPublisher 站内通知发布组件
     * @param systemConfigService 系统动态配置服务
     */
    public TradeOrderService(TradeOrderMapper orderMapper, ProductMapper productMapper, ProductSkuMapper skuMapper,
                             UserMapper userMapper,
                             FlashActivityMapper activityMapper, PickupPointMapper pickupPointMapper,
                             OperatorPickupPointMapper operatorPickupPointMapper,
                             FlashSaleInventoryService inventoryService,
                             FlashSaleStatusWebSocketHandler statusPublisher,
                             NotificationPublisher notificationPublisher,
                             SystemConfigService systemConfigService) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.userMapper = userMapper;
        this.activityMapper = activityMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.operatorPickupPointMapper = operatorPickupPointMapper;
        this.inventoryService = inventoryService;
        this.statusPublisher = statusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 查询我的数据。
     *
     * @param username 当前用户名
     * @param status 业务状态或连接关闭状态
     * @return 查询结果
     */
    public List<TradeOrderEntity> findMine(String username, String status) {
        OrderStatus parsed = parseStatus(status);
        return orderMapper.findByUser(requireUser(username).getId(), parsed);
    }

    /**
     * 查询业务详情。
     *
     * @param id 记录主键
     * @param username 当前用户名
     * @return 查询结果
     */
    public OrderDetailResponse detail(Long id, String username) {
        TradeOrderEntity order = requireOwnedOrder(id, username);
        return new OrderDetailResponse(order, orderMapper.findItems(id), orderMapper.findPaymentByOrder(id),
                orderMapper.findPickupByOrder(id), orderMapper.findRefundByOrder(id));
    }

    /**
     * 创建订单并原子扣减商品与活动库存。
     *
     * @param request 请求参数
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public TradeOrderEntity create(CreateOrderRequest request, String username) {
        UserEntity user = requireUser(username);
        TradeOrderEntity existing = orderMapper.findByRequestNo(request.requestNo().trim());
        if (existing != null) {
            if (!existing.getUserId().equals(user.getId())) {
                throw new BusinessException("REQUEST_NO_USED", "requestNo belongs to another user");
            }
            return existing;
        }
        ProductEntity product = productMapper.findById(request.productId());
        if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_AVAILABLE", "product is not available for ordering");
        }
        if (request.quantity() > product.getLimitPerUser()) {
            throw new BusinessException("LIMIT_EXCEEDED", "quantity exceeds product purchase limit");
        }
        ProductSkuEntity sku = resolveSku(request, product);
        FlashActivityEntity activity = validateActivityOrder(request, user, product);
        PickupPointEntity pickupPoint = resolvePickupPoint(product);
        if (activity != null && activityMapper.decreaseActivityStock(activity.getId(), request.quantity()) != 1) {
            throw new BusinessException("ACTIVITY_STOCK_INSUFFICIENT", "activity stock is insufficient");
        }
        if (sku != null && skuMapper.decreaseStock(sku.getId(), product.getId(), request.quantity()) != 1) {
            throw new BusinessException("SKU_STOCK_INSUFFICIENT", "product sku stock is insufficient");
        }
        if (productMapper.decreaseStock(product.getId(), request.quantity()) != 1) {
            throw new BusinessException("STOCK_INSUFFICIENT", "product stock is insufficient");
        }
        BigDecimal unitPrice = sku == null ? product.getPrice() : sku.getPrice();
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        TradeOrderEntity order = new TradeOrderEntity();
        order.setOrderNo("ORD-" + IdentifierUtil.compactUuid());
        order.setRequestNo(request.requestNo().trim());
        order.setUserId(user.getId());
        order.setActivityId(activity == null ? null : activity.getId());
        order.setPickupPointId(pickupPoint.getId());
        order.setPickupPointName(pickupPoint.getName());
        order.setPickupPointAddress(pickupPoint.getAddress());
        order.setStatus(OrderStatus.WAIT_PAYMENT);
        order.setTotalAmount(amount);
        int paymentTimeoutMinutes = activity == null
                ? systemConfigService.getInt(SystemConfigKey.PAYMENT_TIMEOUT_MINUTES, 15)
                : activity.getPaymentTimeoutMinutes();
        order.setPaymentDeadline(LocalDateTime.now().plusMinutes(paymentTimeoutMinutes));
        orderMapper.insert(order);

        TradeOrderItemEntity item = new TradeOrderItemEntity();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setSkuId(sku == null ? null : sku.getId());
        item.setProductName(product.getName());
        item.setSkuCode(sku == null ? null : sku.getSkuCode());
        item.setSkuName(sku == null ? null : sku.getName());
        item.setUnitPrice(unitPrice);
        item.setQuantity(request.quantity());
        item.setLineAmount(amount);
        orderMapper.insertItem(item);
        statusPublisher.publishOrderAfterCommit(order);
        return order;
    }

    /**
     * 支付。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public PaymentRecordEntity pay(Long id, PayOrderRequest request, String username) {
        UserEntity user = requireUser(username);
        PaymentRecordEntity existingPayment = orderMapper.findPaymentByKey(request.idempotencyKey().trim());
        if (existingPayment != null) {
            TradeOrderEntity existingOrder = orderMapper.findById(existingPayment.getOrderId());
            if (existingOrder == null || !existingOrder.getUserId().equals(user.getId())) {
                throw new BusinessException("PAYMENT_KEY_USED", "idempotency key belongs to another user");
            }
            if (!existingPayment.getOrderId().equals(id)) {
                throw new BusinessException("PAYMENT_KEY_USED", "idempotency key belongs to another order");
            }
            return existingPayment;
        }
        TradeOrderEntity order = requireOwnedOrder(id, username);
        if (order.getStatus() != OrderStatus.WAIT_PAYMENT) {
            throw new BusinessException("ORDER_NOT_PAYABLE", "order is not awaiting payment");
        }
        if (order.getPaymentDeadline() != null && LocalDateTime.now().isAfter(order.getPaymentDeadline())) {
            throw new BusinessException("ORDER_PAYMENT_EXPIRED", "order payment deadline has passed");
        }
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setOrderId(id);
        payment.setPaymentNo("PAY-" + IdentifierUtil.compactUuid());
        payment.setIdempotencyKey(request.idempotencyKey().trim());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        orderMapper.insertPayment(payment);
        if (orderMapper.updateStatus(id, OrderStatus.WAIT_VERIFICATION, OrderStatus.WAIT_PAYMENT) != 1) {
            throw new BusinessException("ORDER_STATE_CHANGED", "order state changed, please retry");
        }
        PickupVerificationEntity pickup = new PickupVerificationEntity();
        pickup.setOrderId(id);
        pickup.setPickupCode(generatePickupCode());
        orderMapper.insertPickupVerification(pickup);
        order.setStatus(OrderStatus.WAIT_VERIFICATION);
        statusPublisher.publishOrderAfterCommit(order);
        notificationPublisher.publishAfterCommit(new NotificationMessage(
                order.getUserId(), NotificationType.PAYMENT_SUCCESS, "payment:" + payment.getPaymentNo(),
                "支付成功，等待领取", "订单 " + order.getOrderNo() + " 已支付，请前往 "
                + order.getPickupPointName() + " 出示提货码。", "/orders?orderId=" + order.getId()));
        return payment;
    }

    /**
     * 自提核销。
     *
     * @param id 记录主键
     * @param username 当前用户名
     * @return 方法执行结果
     */
    public PickupVerificationEntity pickup(Long id, String username) {
        TradeOrderEntity order = requireOwnedOrder(id, username);
        if (order.getStatus() != OrderStatus.WAIT_VERIFICATION && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("PICKUP_NOT_AVAILABLE", "pickup code is not available for this order");
        }
        PickupVerificationEntity pickup = orderMapper.findPickupByOrder(id);
        if (pickup == null) {
            throw new BusinessException("PICKUP_NOT_FOUND", "pickup code does not exist");
        }
        return pickup;
    }

    /**
     * 校验提货码并完成订单核销。
     *
     * @param pickupCode 提货核销码
     * @param operatorUsername 操作人用户名
     * @return 方法执行结果
     */
    @Transactional
    public TradeOrderEntity verifyPickup(String pickupCode, String operatorUsername) {
        UserEntity operator = requireOperator(operatorUsername);
        PickupVerificationEntity pickup = orderMapper.findPickupByCode(pickupCode.trim());
        if (pickup == null) {
            throw new BusinessException("PICKUP_CODE_INVALID", "pickup code is invalid");
        }
        if (pickup.getVerifiedAt() != null) {
            throw new BusinessException("ORDER_ALREADY_VERIFIED", "order has already been verified");
        }
        TradeOrderEntity order = orderMapper.findById(pickup.getOrderId());
        if (order == null || order.getStatus() != OrderStatus.WAIT_VERIFICATION) {
            throw new BusinessException("ORDER_NOT_VERIFIABLE", "order is not awaiting verification");
        }
        if (operator.getRole() != UserRole.ADMIN
                && !operatorPickupPointMapper.exists(operator.getId(), order.getPickupPointId())) {
            throw new BusinessException("PICKUP_POINT_FORBIDDEN", "operator is not authorized for this pickup point");
        }
        if (orderMapper.markPickupVerified(pickup.getId(), operator.getId()) != 1
                || orderMapper.updateStatus(order.getId(), OrderStatus.COMPLETED,
                OrderStatus.WAIT_VERIFICATION) != 1) {
            throw new BusinessException("ORDER_STATE_CHANGED", "order state changed, please retry");
        }
        order.setStatus(OrderStatus.COMPLETED);
        statusPublisher.publishOrderAfterCommit(order);
        notificationPublisher.publishAfterCommit(new NotificationMessage(
                order.getUserId(), NotificationType.PICKUP_VERIFIED, "pickup:" + pickup.getId(),
                "领取已核销", "订单 " + order.getOrderNo() + " 已完成核销，感谢领取校园文创。",
                "/orders?orderId=" + order.getId()));
        return order;
    }

    /**
     * 提交订单退款申请。
     *
     * @param id 记录主键
     * @param reason 操作原因
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public RefundRecordEntity requestRefund(Long id, String reason, String username) {
        TradeOrderEntity order = requireOwnedOrder(id, username);
        RefundRecordEntity existing = orderMapper.findRefundByOrder(id);
        if (existing != null) {
            return existing;
        }
        if (order.getStatus() != OrderStatus.WAIT_VERIFICATION) {
            throw new BusinessException("ORDER_NOT_REFUNDABLE", "only paid, unverified orders can request a refund");
        }
        RefundRecordEntity refund = new RefundRecordEntity();
        refund.setOrderId(id);
        refund.setRefundNo("REF-" + IdentifierUtil.compactUuid());
        refund.setReason(reason.trim());
        refund.setStatus(RefundStatus.PENDING);
        orderMapper.insertRefund(refund);
        if (orderMapper.updateStatus(id, OrderStatus.REFUNDING, OrderStatus.WAIT_VERIFICATION) != 1) {
            throw new BusinessException("ORDER_STATE_CHANGED", "order state changed, please retry");
        }
        order.setStatus(OrderStatus.REFUNDING);
        statusPublisher.publishOrderAfterCommit(order);
        return refund;
    }

    /**
     * 审批退款申请并推进订单状态。
     *
     * @param id 记录主键
     * @param reason 操作原因
     * @param approve 是否审批通过
     * @param operatorUsername 操作人用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public RefundRecordEntity processRefund(Long id, String reason, boolean approve, String operatorUsername) {
        UserEntity operator = requireOperator(operatorUsername);
        TradeOrderEntity order = orderMapper.findById(id);
        RefundRecordEntity refund = orderMapper.findRefundByOrder(id);
        if (order == null || refund == null) {
            throw new BusinessException("REFUND_NOT_FOUND", "refund request does not exist");
        }
        if (refund.getStatus() != RefundStatus.PENDING || order.getStatus() != OrderStatus.REFUNDING) {
            throw new BusinessException("REFUND_ALREADY_PROCESSED", "refund request has already been processed");
        }
        RefundStatus targetStatus = approve ? RefundStatus.APPROVED : RefundStatus.REJECTED;
        OrderStatus targetOrderStatus = approve ? OrderStatus.REFUNDED : OrderStatus.WAIT_VERIFICATION;
        if (orderMapper.updateRefundStatus(refund.getId(), targetStatus, operator.getId(), reason.trim(),
                RefundStatus.PENDING) != 1
                || orderMapper.updateStatus(id, targetOrderStatus, OrderStatus.REFUNDING) != 1) {
            throw new BusinessException("REFUND_STATE_CHANGED", "refund state changed, please retry");
        }
        if (approve) {
            restoreOrderStock(order, orderMapper.findItems(id));
        }
        order.setStatus(targetOrderStatus);
        statusPublisher.publishOrderAfterCommit(order);
        refund.setStatus(targetStatus);
        refund.setProcessedBy(operator.getId());
        refund.setProcessReason(reason.trim());
        return refund;
    }

    /**
     * 取消。
     *
     * @param id 记录主键
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public TradeOrderEntity cancel(Long id, String username) {
        TradeOrderEntity order = requireOwnedOrder(id, username);
        if (order.getStatus() != OrderStatus.WAIT_PAYMENT) {
            throw new BusinessException("ORDER_NOT_CANCELLABLE", "only unpaid orders can be cancelled");
        }
        if (orderMapper.updateStatus(id, OrderStatus.CANCELLED, OrderStatus.WAIT_PAYMENT) != 1) {
            throw new BusinessException("ORDER_STATE_CHANGED", "order state changed, please retry");
        }
        restoreOrderStock(order, orderMapper.findItems(id));
        order.setStatus(OrderStatus.CANCELLED);
        statusPublisher.publishOrderAfterCommit(order);
        return order;
    }

    /**
     * 关闭超过支付期限的待支付订单并恢复库存。
     */
    @Scheduled(fixedDelayString = "${app.order.expiration-scan-ms:60000}")
    @Transactional
    public void closeExpiredOrders() {
        closeExpiredOrders(LocalDateTime.now());
    }

    /**
     * 关闭超过支付期限的待支付订单并恢复库存。
     *
     * @param now 当前业务时间
     * @return 处理结果数量
     */
    @Transactional
    public int closeExpiredOrders(LocalDateTime now) {
        int closed = 0;
        for (TradeOrderEntity order : orderMapper.findExpiredPaymentOrders(now)) {
            if (orderMapper.updateStatus(order.getId(), OrderStatus.CANCELLED, OrderStatus.WAIT_PAYMENT) != 1) {
                continue;
            }
            List<TradeOrderItemEntity> items = orderMapper.findItems(order.getId());
            restoreOrderStock(order, items);
            order.setStatus(OrderStatus.CANCELLED);
            statusPublisher.publishOrderAfterCommit(order);
            closed++;
        }
        return closed;
    }

    /**
     * 查询并校验用户。
     *
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private UserEntity requireUser(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_NOT_AVAILABLE", "user account is not available");
        }
        return user;
    }

    /**
     * 校验活动订单资格、限购和库存条件。
     *
     * @param request 请求参数
     * @param user 用户信息
     * @param product 商品信息
     * @return 方法执行结果
     */
    private FlashActivityEntity validateActivityOrder(CreateOrderRequest request, UserEntity user,
                                                       ProductEntity product) {
        if (request.activityId() == null) return null;
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
        if (request.quantity() > activity.getLimitPerUser()) {
            throw new BusinessException("ACTIVITY_LIMIT_EXCEEDED", "quantity exceeds activity purchase limit");
        }
        int purchased = orderMapper.sumActiveQuantityByActivityAndUser(activity.getId(), user.getId());
        if (purchased + request.quantity() > activity.getLimitPerUser()) {
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
     * 恢复取消、超时或退款订单占用的库存。
     *
     * @param order 订单信息
     * @param items 数据列表
     */
    private void restoreOrderStock(TradeOrderEntity order, List<TradeOrderItemEntity> items) {
        if (items.isEmpty()) {
            throw new BusinessException("ORDER_ITEM_MISSING", "order item is missing");
        }
        int activityQuantity = 0;
        for (TradeOrderItemEntity item : items) {
            if (item.getSkuId() != null
                    && skuMapper.restoreStock(item.getSkuId(), item.getProductId(), item.getQuantity()) != 1) {
                throw new BusinessException("SKU_STOCK_RESTORE_FAILED", "failed to restore product sku stock");
            }
            if (productMapper.restoreStock(item.getProductId(), item.getQuantity()) != 1) {
                throw new BusinessException("STOCK_RESTORE_FAILED", "failed to restore product stock");
            }
            activityQuantity += item.getQuantity();
        }
        if (order.getActivityId() != null
                && activityMapper.restoreActivityStock(order.getActivityId(), activityQuantity) != 1) {
            throw new BusinessException("ACTIVITY_STOCK_RESTORE_FAILED", "failed to restore activity stock");
        }
        if (order.getActivityId() != null) {
            inventoryService.release(order.getActivityId(), order.getUserId(), activityQuantity,
                    order.getRequestNo(), "RELEASED");
        }
    }

    /**
     * 校验订单规格选择。
     *
     * @param request 下单请求
     * @param product 商品信息
     * @return 已选择规格，无规格商品返回空
     */
    private ProductSkuEntity resolveSku(CreateOrderRequest request, ProductEntity product) {
        int skuCount = skuMapper.countByProductId(product.getId());
        if (skuCount == 0) {
            if (request.skuId() != null) {
                throw new BusinessException("SKU_NOT_FOUND", "product sku does not exist");
            }
            return null;
        }
        if (request.skuId() == null) {
            throw new BusinessException("SKU_REQUIRED", "skuId is required for this product");
        }
        ProductSkuEntity sku = skuMapper.findById(request.skuId());
        if (sku == null || !sku.getProductId().equals(product.getId()) || !Boolean.TRUE.equals(sku.getEnabled())) {
            throw new BusinessException("SKU_NOT_AVAILABLE", "product sku is not available");
        }
        return sku;
    }

    /**
     * 解析订单履约自提点并校验当前可用性。
     *
     * @param product 下单商品
     * @return 订单履约自提点
     */
    private PickupPointEntity resolvePickupPoint(ProductEntity product) {
        Long pickupPointId = product.getPickupPointId();
        if (pickupPointId == null) {
            throw new BusinessException("PICKUP_POINT_REQUIRED", "product pickup point is required for ordering");
        }
        PickupPointEntity pickupPoint = pickupPointMapper.findById(pickupPointId);
        if (pickupPoint == null || pickupPoint.getStatus() != PickupPointStatus.ACTIVE) {
            throw new BusinessException("PICKUP_POINT_NOT_AVAILABLE", "pickup point is not active");
        }
        return pickupPoint;
    }

    /**
     * 查询并校验运营操作人。
     *
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private UserEntity requireOperator(String username) {
        UserEntity user = requireUser(username);
        if (!user.getRole().isMerchant() && user.getRole() != UserRole.ADMIN) {
            throw new BusinessException("FORBIDDEN", "operator permission is required");
        }
        return user;
    }

    /**
     * 查询并校验归属当前用户的订单。
     *
     * @param id 记录主键
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private TradeOrderEntity requireOwnedOrder(Long id, String username) {
        UserEntity user = requireUser(username);
        TradeOrderEntity order = orderMapper.findById(id);
        if (order == null || !order.getUserId().equals(user.getId())) {
            throw new BusinessException("ORDER_NOT_FOUND", "order does not exist");
        }
        return order;
    }

    /**
     * 解析状态。
     *
     * @param status 业务状态或连接关闭状态
     * @return 转换后的结果
     */
    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_ORDER_STATUS", "order status is not supported");
        }
    }

    /**
     * 生成一次性提货核销码。
     *
     * @return 转换后的结果
     */
    private String generatePickupCode() {
        return IdentifierUtil.compactUuid().substring(0, 12).toUpperCase();
    }
}
