package com.sk.onlinemall.order.service;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.order.mapper.FlashSaleRequestMapper;
import com.sk.onlinemall.order.mapper.InventoryReconciliationMapper;
import com.sk.onlinemall.order.mapper.TradeOrderMapper;
import com.sk.onlinemall.order.model.InventoryReconciliationEntity;
import com.sk.onlinemall.order.model.InventorySnapshot;
import com.sk.onlinemall.order.model.UserQuantity;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryReconciliationService {
    private final FlashActivityMapper activityMapper;
    private final FlashSaleRequestMapper requestMapper;
    private final TradeOrderMapper orderMapper;
    private final InventoryReconciliationMapper reconciliationMapper;
    private final UserMapper userMapper;
    private final FlashSaleInventoryService inventoryService;

    /**
     * 创建 InventoryReconciliationService 实例。
     *
     * @param activityMapper 活动数据访问组件
     * @param requestMapper 请求数据访问组件
     * @param orderMapper 订单数据访问组件
     * @param reconciliationMapper 库存校准数据访问组件
     * @param userMapper 用户数据访问组件
     * @param inventoryService 库存业务服务
     */
    public InventoryReconciliationService(FlashActivityMapper activityMapper,
                                          FlashSaleRequestMapper requestMapper,
                                          TradeOrderMapper orderMapper,
                                          InventoryReconciliationMapper reconciliationMapper,
                                          UserMapper userMapper,
                                          FlashSaleInventoryService inventoryService) {
        this.activityMapper = activityMapper;
        this.requestMapper = requestMapper;
        this.orderMapper = orderMapper;
        this.reconciliationMapper = reconciliationMapper;
        this.userMapper = userMapper;
        this.inventoryService = inventoryService;
    }

    /**
     * 库存快照。
     *
     * @param activityId 活动主键
     * @param username 当前用户名
     * @return 查询结果
     */
    public InventorySnapshot snapshot(Long activityId, String username) {
        requireOperator(username);
        FlashActivityEntity activity = requireActivity(activityId);
        int pendingQuantity = requestMapper.sumPendingQuantity(activityId);
        int expectedRedisStock = activity.getStock() - pendingQuantity;
        Integer redisStock = inventoryService.currentStock(activityId);
        return new InventorySnapshot(activityId, activity.getName(), activity.getStock(), redisStock,
                pendingQuantity, orderMapper.sumPaidQuantityByActivity(activityId), expectedRedisStock,
                redisStock != null, redisStock != null && redisStock == expectedRedisStock, LocalDateTime.now());
    }

    /**
     * 查询活动最近的库存校准记录。
     *
     * @param activityId 活动主键
     * @param username 当前用户名
     * @return 查询结果
     */
    public List<InventoryReconciliationEntity> recent(Long activityId, String username) {
        requireOperator(username);
        requireActivity(activityId);
        return reconciliationMapper.findRecent(activityId);
    }

    /**
     * 校准库存。
     *
     * @param activityId 活动主键
     * @param reason 操作原因
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    public InventoryReconciliationEntity reconcile(Long activityId, String reason, String username) {
        UserEntity operator = requireOperator(username);
        String lockToken = inventoryService.acquireReconciliationLock(activityId);
        if (lockToken == null) {
            throw new BusinessException("INVENTORY_RECONCILIATION_RUNNING",
                    "activity inventory reconciliation is already running");
        }
        try {
            awaitAcceptingRequests(activityId);
            FlashActivityEntity activity = requireActivity(activityId);
            int pendingQuantity = requestMapper.sumPendingQuantity(activityId);
            int expectedRedisStock = activity.getStock() - pendingQuantity;
            if (expectedRedisStock < 0) {
                throw new BusinessException("INVENTORY_RECONCILIATION_INVALID",
                        "pending quantity exceeds database inventory");
            }
            Integer redisStockBefore = inventoryService.currentStock(activityId);
            InventoryReconciliationEntity audit = new InventoryReconciliationEntity();
            audit.setActivityId(activityId);
            audit.setDatabaseStock(activity.getStock());
            audit.setRedisStockBefore(redisStockBefore);
            audit.setRedisStockAfter(expectedRedisStock);
            audit.setPendingQuantity(pendingQuantity);
            audit.setDifferenceBefore(redisStockBefore == null ? null : expectedRedisStock - redisStockBefore);
            audit.setReason(reason.trim());
            audit.setAdjustedBy(operator.getId());
            audit.setStatus("PENDING");
            reconciliationMapper.insert(audit);
            try {
                inventoryService.rebuild(activity, expectedRedisStock, expectedBuyerQuantities(activityId));
                audit.setStatus("SUCCEEDED");
                reconciliationMapper.updateResult(audit);
            } catch (RuntimeException exception) {
                audit.setStatus("FAILED");
                audit.setFailureMessage(TextUtil.truncate(exception.getMessage(), 500));
                reconciliationMapper.updateResult(audit);
                throw exception;
            }
            return reconciliationMapper.findById(audit.getId());
        } finally {
            inventoryService.releaseReconciliationLock(activityId, lockToken);
        }
    }

    /**
     * 统计库存校准所需的用户累计购买数量。
     *
     * @param activityId 活动主键
     * @return 方法执行结果
     */
    private Map<Long, Integer> expectedBuyerQuantities(Long activityId) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        merge(quantities, orderMapper.findActiveQuantityByActivityGroupedByUser(activityId));
        merge(quantities, requestMapper.findPendingQuantityByUser(activityId));
        return quantities;
    }

    /**
     * 等待活动中正在受理的抢购请求完成。
     *
     * @param activityId 活动主键
     */
    private void awaitAcceptingRequests(Long activityId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (requestMapper.countAccepting(activityId) == 0) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException("INVENTORY_RECONCILIATION_INTERRUPTED",
                        "inventory reconciliation was interrupted");
            }
        }
        throw new BusinessException("INVENTORY_RECONCILIATION_BUSY",
                "flash-sale requests are still being accepted, please retry");
    }

    /**
     * 合并用户购买数量统计。
     *
     * @param target 目标数据映射
     * @param source 待合并的数据来源
     */
    private void merge(Map<Long, Integer> target, List<UserQuantity> source) {
        for (UserQuantity item : source) {
            target.merge(item.getUserId(), item.getQuantity(), Integer::sum);
        }
    }

    /**
     * 查询并校验活动。
     *
     * @param activityId 活动主键
     * @return 方法执行结果
     */
    private FlashActivityEntity requireActivity(Long activityId) {
        FlashActivityEntity activity = activityMapper.findById(activityId);
        if (activity == null) throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        return activity;
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
                || !user.getRole().isMerchant() && user.getRole() != UserRole.ADMIN) {
            throw new BusinessException("OPERATOR_NOT_AVAILABLE", "operator account is not available");
        }
        return user;
    }

}
