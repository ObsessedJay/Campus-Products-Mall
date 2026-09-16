package com.sk.onlinemall.activity.service;

import com.sk.onlinemall.activity.dto.CreateActivityRequest;
import com.sk.onlinemall.activity.dto.RunLotteryRequest;
import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.ActivityMode;
import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.ActivityReservationEntity;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.LotteryBatchEntity;
import com.sk.onlinemall.activity.model.LotteryDrawResult;
import com.sk.onlinemall.activity.model.ReservationStatus;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.IdentifierUtil;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointStatus;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.realtime.FlashSaleStatusWebSocketHandler;
import com.sk.onlinemall.notification.model.NotificationMessage;
import com.sk.onlinemall.notification.model.NotificationType;
import com.sk.onlinemall.notification.service.NotificationPublisher;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.service.SystemConfigService;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class FlashActivityService {
    private final FlashActivityMapper activityMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final PickupPointMapper pickupPointMapper;
    private final FlashSaleStatusWebSocketHandler statusPublisher;
    private final ActivityLifecycleService lifecycleService;
    private final NotificationPublisher notificationPublisher;
    private final SystemConfigService systemConfigService;

    /**
     * 创建 FlashActivityService 实例。
     *
     * @param activityMapper 活动数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     * @param pickupPointMapper 自提点数据访问组件
     * @param statusPublisher 实时状态发布组件
     * @param lifecycleService 活动生命周期服务
     * @param notificationPublisher 站内通知发布组件
     * @param systemConfigService 系统动态配置服务
     */
    @Autowired
    public FlashActivityService(FlashActivityMapper activityMapper, ProductMapper productMapper,
                                UserMapper userMapper, PickupPointMapper pickupPointMapper,
                                FlashSaleStatusWebSocketHandler statusPublisher,
                                ActivityLifecycleService lifecycleService,
                                NotificationPublisher notificationPublisher,
                                SystemConfigService systemConfigService) {
        this.activityMapper = activityMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.statusPublisher = statusPublisher;
        this.lifecycleService = lifecycleService;
        this.notificationPublisher = notificationPublisher;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 创建兼容独立单元测试的活动服务。
     *
     * @param activityMapper 活动数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     * @param pickupPointMapper 自提点数据访问组件
     * @param statusPublisher 实时状态发布组件
     * @param lifecycleService 活动生命周期服务
     * @param notificationPublisher 站内通知发布组件
     */
    public FlashActivityService(FlashActivityMapper activityMapper, ProductMapper productMapper,
                                UserMapper userMapper, PickupPointMapper pickupPointMapper,
                                FlashSaleStatusWebSocketHandler statusPublisher,
                                ActivityLifecycleService lifecycleService,
                                NotificationPublisher notificationPublisher) {
        this(activityMapper, productMapper, userMapper, pickupPointMapper, statusPublisher,
                lifecycleService, notificationPublisher, null);
    }

    /**
     * 查询公开数据。
     *
     * @return 查询结果
     */
    public List<FlashActivityEntity> findPublic() {
        return activityMapper.findPublic();
    }

    /**
     * 查询运营侧全部活动。
     *
     * @return 全部状态活动
     */
    public List<FlashActivityEntity> findForManagement() {
        return activityMapper.findForManagement();
    }

    /**
     * 查询公开数据查询详情。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    public FlashActivityEntity findPublicDetail(Long id) {
        FlashActivityEntity activity = activityMapper.findById(id);
        if (activity == null || activity.getStatus() == ActivityStatus.UNPUBLISHED
                || activity.getStatus() == ActivityStatus.PENDING_REVIEW
                || activity.getStatus() == ActivityStatus.REJECTED
                || activity.getStatus() == ActivityStatus.TERMINATED) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        }
        return activity;
    }

    /**
     * 使用系统默认操作人创建抢购活动。
     *
     * @param request 请求参数
     * @return 处理后的业务数据
     */
    @Transactional
    public FlashActivityEntity create(CreateActivityRequest request) {
        return create(request, null);
    }

    /**
     * 校验商品与时间规则并创建抢购活动。
     *
     * @param request 请求参数
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public FlashActivityEntity create(CreateActivityRequest request, String username) {
        validateRequest(request);
        FlashActivityEntity activity = new FlashActivityEntity();
        if (username != null) {
            var creator = userMapper.findByUsername(username);
            activity.setCreatedBy(creator == null ? null : creator.getId());
        }
        applyRequest(activity, request);
        activity.setStatus(ActivityStatus.UNPUBLISHED);
        activity.setReviewStatus("PENDING");
        activityMapper.insert(activity);
        return activity;
    }

    /**
     * 更新未发布或已驳回的活动并重新进入审核。
     *
     * @param id 活动主键
     * @param request 更新请求
     * @return 更新后的活动
     */
    @Transactional
    public FlashActivityEntity update(Long id, CreateActivityRequest request) {
        FlashActivityEntity activity = requireActivity(id);
        if (activity.getStatus() != ActivityStatus.UNPUBLISHED
                && activity.getStatus() != ActivityStatus.PENDING_REVIEW
                && activity.getStatus() != ActivityStatus.REJECTED) {
            throw new BusinessException("ACTIVITY_NOT_EDITABLE", "published activities cannot be edited");
        }
        validateRequest(request);
        applyRequest(activity, request);
        activity.setStatus(lifecycleService.transition(activity.getStatus(), ActivityEvent.EDIT));
        activity.setReviewStatus("PENDING");
        if (activityMapper.updateForManagement(activity) != 1) {
            throw new BusinessException("ACTIVITY_STATE_CHANGED", "activity state changed, please refresh and retry");
        }
        return activityMapper.findById(id);
    }

    /**
     * 发布审核通过的抢购活动并同步实时状态。
     *
     * @param id 记录主键
     * @return 处理后的业务数据
     */
    @Transactional
    public FlashActivityEntity publish(Long id) {
        FlashActivityEntity activity = requireActivity(id);
        if (activity.getStatus() != ActivityStatus.UNPUBLISHED) {
            throw new BusinessException("ACTIVITY_NOT_EDITABLE", "only unpublished activity can be published");
        }
        if (!"APPROVED".equals(activity.getReviewStatus())) {
            throw new BusinessException("ACTIVITY_NOT_APPROVED", "activity must pass review before publishing");
        }
        var product = productMapper.findById(activity.getProductId());
        if (product == null || product.getStatus() != ProductStatus.ON_SALE
                || activity.getStock() > product.getStock()) {
            throw new BusinessException("ACTIVITY_NOT_READY", "activity product or stock is not ready");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(activity.getStartAt()) || !activity.getStartAt().isBefore(activity.getEndAt())) {
            throw new BusinessException("ACTIVITY_NOT_READY", "activity time window is no longer valid");
        }
        ActivityEvent publishEvent = activity.getReservationEndAt() != null
                && now.isBefore(activity.getReservationEndAt())
                ? ActivityEvent.PUBLISH_WITH_RESERVATION : ActivityEvent.PUBLISH_DIRECT;
        ActivityStatus next = lifecycleService.transition(activity.getStatus(), publishEvent);
        activity.setStatus(next);
        if (activityMapper.updateStatusIfCurrent(id, next, ActivityStatus.UNPUBLISHED) != 1) {
            throw new BusinessException("ACTIVITY_STATE_CHANGED", "activity state changed, please refresh and retry");
        }
        statusPublisher.publishActivityAfterCommit(activity);
        return activity;
    }

    /**
     * 创建当前用户的活动预约记录。
     *
     * @param activityId 活动主键
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public ActivityReservationEntity reserve(Long activityId, String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_NOT_AVAILABLE", "user account is not available");
        }
        FlashActivityEntity activity = requireActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStatus() != ActivityStatus.RESERVING
                || activity.getStartAt() == null || !now.isBefore(activity.getStartAt())
                || activity.getReservationStartAt() != null && now.isBefore(activity.getReservationStartAt())
                || activity.getReservationEndAt() != null && now.isAfter(activity.getReservationEndAt())) {
            throw new BusinessException("RESERVATION_CLOSED", "activity is not accepting reservations");
        }
        ActivityReservationEntity existing = activityMapper.findReservation(activityId, user.getId());
        if (existing != null) {
            return existing;
        }
        ActivityReservationEntity reservation = new ActivityReservationEntity();
        reservation.setActivityId(activityId);
        reservation.setUserId(user.getId());
        reservation.setReservationNo("RSV-" + IdentifierUtil.compactUuid());
        reservation.setStatus(activity.getMode().name().equals("LOTTERY")
                ? ReservationStatus.PENDING : ReservationStatus.QUALIFIED);
        try {
            activityMapper.insertReservation(reservation);
        } catch (DuplicateKeyException exception) {
            return activityMapper.findReservation(activityId, user.getId());
        }
        notificationPublisher.publishAfterCommit(new NotificationMessage(
                user.getId(), NotificationType.ACTIVITY_REMINDER,
                "activity-reminder:" + activity.getId() + ":" + user.getId(),
                "活动预约提醒", activity.getName() + " 将于 " + activity.getStartAt() + " 开始，请留意资格与库存。",
                "/activities/" + activity.getId()));
        return reservation;
    }

    /**
     * 查询当前用户的活动预约。
     *
     * @param activityId 活动主键
     * @param username 当前用户名
     * @return 查询结果
     */
    public ActivityReservationEntity findMyReservation(Long activityId, String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_AVAILABLE", "user account is not available");
        }
        ActivityReservationEntity reservation = activityMapper.findReservation(activityId, user.getId());
        if (reservation == null) {
            throw new BusinessException("RESERVATION_NOT_FOUND", "reservation does not exist");
        }
        return reservation;
    }

    /**
     * 执行活动抽签并保存可追溯结果。
     *
     * @param activityId 活动主键
     * @param request 请求参数
     * @param username 当前用户名
     * @return 方法执行结果
     */
    @Transactional
    public LotteryDrawResult runLottery(Long activityId, RunLotteryRequest request, String username) {
        FlashActivityEntity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        }
        LotteryBatchEntity existing = activityMapper.findLotteryBatch(activityId);
        if (existing != null) {
            return lotteryResult(existing);
        }
        if (activity.getMode() != ActivityMode.LOTTERY) {
            throw new BusinessException("ACTIVITY_NOT_LOTTERY", "only lottery activities can be drawn");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getReservationEndAt() == null || now.isBefore(activity.getReservationEndAt())) {
            throw new BusinessException("LOTTERY_DRAW_TOO_EARLY", "lottery can run after reservations close");
        }
        if (activity.getStartAt() == null || !now.isBefore(activity.getStartAt())) {
            throw new BusinessException("LOTTERY_DRAW_TOO_LATE", "lottery must run before the activity starts");
        }
        List<ActivityReservationEntity> reservations = activityMapper.findReservations(activityId);
        if (reservations.isEmpty()) {
            throw new BusinessException("LOTTERY_NO_RESERVATIONS", "lottery has no reservations");
        }

        int requestedWinners = request.winnerCount() == null ? activity.getStock() : request.winnerCount();
        if (requestedWinners > activity.getStock()) {
            throw new BusinessException("LOTTERY_WINNER_LIMIT_EXCEEDED", "winner count exceeds activity stock");
        }
        int winnerCount = Math.min(requestedWinners, reservations.size());
        long seed = request.seed() == null ? new SecureRandom().nextLong() : request.seed();
        List<ActivityReservationEntity> ranked = new ArrayList<>(reservations);
        Collections.shuffle(ranked, new Random(seed));

        UserEntity operator = userMapper.findByUsername(username);
        if (operator == null || operator.getStatus() != UserStatus.ACTIVE
                || !operator.getRole().isMerchant() && operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException("OPERATOR_NOT_AVAILABLE", "operator account is not available");
        }
        LotteryBatchEntity batch = new LotteryBatchEntity();
        batch.setActivityId(activityId);
        batch.setBatchNo("LOT-" + IdentifierUtil.compactUuid().toUpperCase());
        batch.setRandomSeed(seed);
        batch.setTotalReservations(reservations.size());
        batch.setWinnerCount(winnerCount);
        batch.setDrawnBy(operator.getId());
        batch.setDrawnAt(now);
        try {
            activityMapper.insertLotteryBatch(batch);
        } catch (DuplicateKeyException exception) {
            LotteryBatchEntity concurrent = activityMapper.findLotteryBatch(activityId);
            if (concurrent != null) return lotteryResult(concurrent);
            throw exception;
        }

        for (int index = 0; index < ranked.size(); index++) {
            ActivityReservationEntity reservation = ranked.get(index);
            ReservationStatus status = index < winnerCount
                    ? ReservationStatus.QUALIFIED : ReservationStatus.NOT_QUALIFIED;
            if (activityMapper.updateReservationResult(reservation.getId(), status, batch.getId(), index + 1) != 1) {
                throw new BusinessException("LOTTERY_STATE_CHANGED", "reservation state changed during lottery");
            }
            String resultText = status == ReservationStatus.QUALIFIED ? "已获得购买资格" : "本轮未中签";
            notificationPublisher.publishAfterCommit(new NotificationMessage(
                    reservation.getUserId(), NotificationType.LOTTERY_RESULT,
                    "lottery:" + batch.getId() + ":" + reservation.getUserId(),
                    "抽签结果已公布", activity.getName() + "：" + resultText + "，抽签名次 " + (index + 1) + "。",
                    "/activities/" + activity.getId()));
        }
        if (activity.getStatus() == ActivityStatus.RESERVING) {
            ActivityStatus previous = activity.getStatus();
            activity.setStatus(lifecycleService.transition(previous, ActivityEvent.CLOSE_RESERVATION));
            if (activityMapper.updateStatusIfCurrent(activity.getId(), activity.getStatus(), previous) != 1) {
                throw new BusinessException("ACTIVITY_STATE_CHANGED", "activity state changed during lottery");
            }
            statusPublisher.publishActivityAfterCommit(activity);
        }
        return lotteryResult(batch);
    }

    /**
     * 查询抽签。
     *
     * @param activityId 活动主键
     * @return 查询结果
     */
    public LotteryDrawResult findLottery(Long activityId) {
        LotteryBatchEntity batch = activityMapper.findLotteryBatch(activityId);
        if (batch == null) {
            throw new BusinessException("LOTTERY_NOT_DRAWN", "lottery result does not exist");
        }
        return lotteryResult(batch);
    }

    /**
     * 终止。
     *
     * @param id 记录主键
     * @param reason 操作原因
     * @return 处理后的业务数据
     */
    @Transactional
    public FlashActivityEntity terminate(Long id, String reason) {
        FlashActivityEntity activity = requireActivity(id);
        lifecycleService.transition(activity.getStatus(), ActivityEvent.TERMINATE);
        activityMapper.terminate(id, reason.trim());
        FlashActivityEntity terminated = requireActivity(id);
        statusPublisher.publishActivityAfterCommit(terminated);
        return terminated;
    }

    /**
     * 同步活动在当前时间下的业务状态。
     */
    @Scheduled(fixedDelayString = "${app.activity.state-scan-ms:10000}")
    @Transactional
    public void synchronizeActivityStates() {
        synchronizeActivityStates(LocalDateTime.now());
    }

    /**
     * 同步活动在当前时间下的业务状态。
     *
     * @param now 当前业务时间
     * @return 处理结果数量
     */
    @Transactional
    public int synchronizeActivityStates(LocalDateTime now) {
        int changed = 0;
        for (FlashActivityEntity activity : activityMapper.findNeedingStateSync(now)) {
            ActivityEvent event = nextEvent(activity, now);
            if (event == null) continue;
            ActivityStatus previous = activity.getStatus();
            ActivityStatus next = lifecycleService.transition(previous, event);
            if (activityMapper.updateStatusIfCurrent(activity.getId(), next, previous) != 1) continue;
            activity.setStatus(next);
            statusPublisher.publishActivityAfterCommit(activity);
            changed++;
        }
        return changed;
    }

    /**
     * 下一次状态。
     *
     * @param activity 活动信息
     * @param now 当前业务时间
     * @return 转换后的结果
     */
    private ActivityEvent nextEvent(FlashActivityEntity activity, LocalDateTime now) {
        if (activity.getEndAt() != null && !now.isBefore(activity.getEndAt())) return ActivityEvent.FINISH;
        if (activity.getStartAt() != null && !now.isBefore(activity.getStartAt())) return ActivityEvent.START;
        if (activity.getStatus() == ActivityStatus.RESERVING && activity.getReservationEndAt() != null
                && !now.isBefore(activity.getReservationEndAt())) return ActivityEvent.CLOSE_RESERVATION;
        return null;
    }

    /**
     * 查询并校验活动。
     *
     * @param id 记录主键
     * @return 方法执行结果
     */
    private FlashActivityEntity requireActivity(Long id) {
        FlashActivityEntity activity = activityMapper.findById(id);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        }
        return activity;
    }

    /**
     * 组装抽签批次结果。
     *
     * @param batch 抽签批次
     * @return 方法执行结果
     */
    private LotteryDrawResult lotteryResult(LotteryBatchEntity batch) {
        return new LotteryDrawResult(batch, activityMapper.findReservations(batch.getActivityId()));
    }

    /**
     * 校验活动预约和发售时间范围。
     *
     * @param request 请求参数
     */
    private void validateTimes(CreateActivityRequest request) {
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException("INVALID_ACTIVITY_TIME", "startAt must be before endAt");
        }
        if ((request.reservationStartAt() == null) != (request.reservationEndAt() == null)) {
            throw new BusinessException("INVALID_RESERVATION_TIME", "reservation window must be complete");
        }
        if (request.mode() == ActivityMode.LOTTERY && request.reservationStartAt() == null) {
            throw new BusinessException("RESERVATION_TIME_REQUIRED", "lottery activity requires a reservation window");
        }
        if (request.reservationStartAt() != null
                && !request.reservationStartAt().isBefore(request.reservationEndAt())) {
            throw new BusinessException("INVALID_RESERVATION_TIME", "reservationStartAt must be before reservationEndAt");
        }
        if (request.reservationEndAt() != null && !request.reservationEndAt().isBefore(request.startAt())) {
            throw new BusinessException("INVALID_RESERVATION_TIME", "reservation must end before activity starts");
        }
        int leadMinutes = systemConfigService == null ? 0
                : systemConfigService.getInt(SystemConfigKey.RESERVATION_LEAD_MINUTES, 30);
        if (request.reservationEndAt() != null
                && request.reservationEndAt().plusMinutes(leadMinutes).isAfter(request.startAt())) {
            throw new BusinessException("RESERVATION_LEAD_TIME_TOO_SHORT",
                    "reservation must end before the configured activity lead time");
        }
    }

    /**
     * 校验活动商品、库存和时间配置。
     *
     * @param request 活动请求
     */
    private void validateRequest(CreateActivityRequest request) {
        validateTimes(request);
        var product = productMapper.findById(request.productId());
        if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_AVAILABLE", "activity product must be on sale");
        }
        if (request.stock() > product.getStock()) {
            throw new BusinessException("INSUFFICIENT_PRODUCT_STOCK", "activity stock exceeds product stock");
        }
        var pickupPoint = pickupPointMapper.findById(product.getPickupPointId());
        if (pickupPoint == null || pickupPoint.getStatus() != PickupPointStatus.ACTIVE) {
            throw new BusinessException("PICKUP_POINT_NOT_AVAILABLE", "product pickup point is not active");
        }
    }

    /**
     * 将活动请求字段写入活动实体。
     *
     * @param activity 活动实体
     * @param request 活动请求
     */
    private void applyRequest(FlashActivityEntity activity, CreateActivityRequest request) {
        activity.setName(request.name().trim());
        activity.setProductId(request.productId());
        ProductEntity product = productMapper.findById(request.productId());
        activity.setPickupPointId(product.getPickupPointId());
        activity.setMode(request.mode());
        activity.setReservationStartAt(request.reservationStartAt());
        activity.setReservationEndAt(request.reservationEndAt());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activity.setStock(request.stock());
        activity.setLimitPerUser(request.limitPerUser());
        activity.setPaymentTimeoutMinutes(request.paymentTimeoutMinutes());
        activity.setRuleDescription(request.ruleDescription() == null || request.ruleDescription().isBlank()
                ? null : request.ruleDescription().trim());
    }
}
