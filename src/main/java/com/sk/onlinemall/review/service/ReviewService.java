package com.sk.onlinemall.review.service;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.ActivityEvent;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.service.ActivityLifecycleService;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointStatus;
import com.sk.onlinemall.review.mapper.ReviewLogMapper;
import com.sk.onlinemall.review.model.ReviewContentType;
import com.sk.onlinemall.review.model.ReviewItem;
import com.sk.onlinemall.review.model.ReviewLogEntity;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewService {
    private final ProductMapper productMapper;
    private final FlashActivityMapper activityMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final UserMapper userMapper;
    private final ActivityLifecycleService activityLifecycleService;
    private final PickupPointMapper pickupPointMapper;

    /**
     * 创建 ReviewService 实例。
     *
     * @param productMapper 商品数据访问组件
     * @param activityMapper 活动数据访问组件
     * @param reviewLogMapper 审核Log数据访问组件
     * @param userMapper 用户数据访问组件
     * @param activityLifecycleService 活动生命周期服务
     * @param pickupPointMapper 自提点数据访问组件
     */
    public ReviewService(ProductMapper productMapper, FlashActivityMapper activityMapper,
                          ReviewLogMapper reviewLogMapper, UserMapper userMapper,
                          ActivityLifecycleService activityLifecycleService,
                          PickupPointMapper pickupPointMapper) {
        this.productMapper = productMapper;
        this.activityMapper = activityMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.userMapper = userMapper;
        this.activityLifecycleService = activityLifecycleService;
        this.pickupPointMapper = pickupPointMapper;
    }

    /**
     * 查询待审核列表。
     *
     * @param type 审核内容类型
     * @param status 业务状态或连接关闭状态
     * @return 查询结果
     */
    public List<ReviewItem> findPending(String type, String status) {
        ReviewContentType contentType = ReviewContentType.parse(type);
        if (status != null && !status.isBlank() && !"PENDING_REVIEW".equalsIgnoreCase(status.trim())) {
            throw new BusinessException("INVALID_REVIEW_STATUS", "only PENDING_REVIEW can be queried");
        }
        List<ReviewItem> items = new ArrayList<>();
        if (contentType == null || contentType == ReviewContentType.PRODUCT) {
            for (ProductEntity product : productMapper.findPendingReview()) {
                items.add(new ReviewItem(ReviewContentType.PRODUCT, product.getId(), product.getName(),
                        product.getStatus().name(), ProductStatus.PENDING_REVIEW.name(), submitter(product.getCreatedBy()), product.getCreatedAt()));
            }
        }
        if (contentType == null || contentType == ReviewContentType.ACTIVITY) {
            for (FlashActivityEntity activity : activityMapper.findPendingReview()) {
                items.add(new ReviewItem(ReviewContentType.ACTIVITY, activity.getId(), activity.getName(),
                        activity.getStatus().name(), "PENDING_REVIEW", submitter(activity.getCreatedBy()), activity.getCreatedAt()));
            }
        }
        return items;
    }

    /**
     * 通过审核。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param reviewerUsername 审核人用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public Object approve(String type, Long id, String reviewerUsername) {
        ReviewContentType contentType = parseRequiredType(type);
        ReviewLogEntity log = baseLog(contentType, id, reviewerUsername, "APPROVED", null);
        if (contentType == ReviewContentType.PRODUCT) {
            ProductEntity product = requireProduct(id);
            if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
                throw new BusinessException("REVIEW_NOT_PENDING", "product is not pending review");
            }
            var pickupPoint = pickupPointMapper.findById(product.getPickupPointId());
            if (pickupPoint == null || pickupPoint.getStatus() != PickupPointStatus.ACTIVE) {
                throw new BusinessException("PICKUP_POINT_NOT_AVAILABLE", "product pickup point is not active");
            }
            product.setStatus(ProductStatus.ON_SALE);
            if (productMapper.updateReviewStatus(product) != 1) {
                throw new BusinessException("REVIEW_NOT_PENDING", "product is not pending review");
            }
            reviewLogMapper.insert(log);
            return productMapper.findById(id);
        }
        FlashActivityEntity activity = requireActivity(id);
        if (!(activity.getStatus() == ActivityStatus.UNPUBLISHED
                || activity.getStatus() == ActivityStatus.PENDING_REVIEW)
                || "APPROVED".equals(activity.getReviewStatus())) {
            throw new BusinessException("REVIEW_NOT_PENDING", "activity is not pending review");
        }
        ActivityStatus expectedStatus = activity.getStatus();
        activity.setStatus(activityLifecycleService.transition(expectedStatus, ActivityEvent.REVIEW_APPROVE));
        activity.setReviewStatus("APPROVED");
        if (activityMapper.updateReviewStatus(activity, expectedStatus) != 1) {
            throw new BusinessException("REVIEW_NOT_PENDING", "activity is not pending review");
        }
        reviewLogMapper.insert(log);
        return activityMapper.findById(id);
    }

    /**
     * 驳回。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param reason 操作原因
     * @param reviewerUsername 审核人用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public Object reject(String type, Long id, String reason, String reviewerUsername) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REVIEW_REASON_REQUIRED", "rejection reason is required");
        }
        ReviewContentType contentType = parseRequiredType(type);
        ReviewLogEntity log = baseLog(contentType, id, reviewerUsername, "REJECTED", reason.trim());
        if (contentType == ReviewContentType.PRODUCT) {
            ProductEntity product = requireProduct(id);
            if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
                throw new BusinessException("REVIEW_NOT_PENDING", "product is not pending review");
            }
            product.setStatus(ProductStatus.REJECTED);
            if (productMapper.updateReviewStatus(product) != 1) {
                throw new BusinessException("REVIEW_NOT_PENDING", "product is not pending review");
            }
            reviewLogMapper.insert(log);
            return productMapper.findById(id);
        }
        FlashActivityEntity activity = requireActivity(id);
        if (!(activity.getStatus() == ActivityStatus.UNPUBLISHED
                || activity.getStatus() == ActivityStatus.PENDING_REVIEW)
                || "APPROVED".equals(activity.getReviewStatus())) {
            throw new BusinessException("REVIEW_NOT_PENDING", "activity is not pending review");
        }
        ActivityStatus expectedStatus = activity.getStatus();
        activity.setStatus(activityLifecycleService.transition(expectedStatus, ActivityEvent.REVIEW_REJECT));
        activity.setReviewStatus("REJECTED");
        if (activityMapper.updateReviewStatus(activity, expectedStatus) != 1) {
            throw new BusinessException("REVIEW_NOT_PENDING", "activity is not pending review");
        }
        reviewLogMapper.insert(log);
        return activityMapper.findById(id);
    }

    /**
     * 查询审核日志。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @return 查询结果
     */
    public List<ReviewLogEntity> findLogs(String type, Long id) {
        return findLogs(type, id, 100);
    }

    /**
     * 按条件查询限定数量的审核日志。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param limit 最大返回数量
     * @return 查询结果
     */
    public List<ReviewLogEntity> findLogs(String type, Long id, Integer limit) {
        if (limit == null || limit < 1 || limit > 100) {
            throw new BusinessException("INVALID_REVIEW_LOG_LIMIT", "limit must be between 1 and 100");
        }
        ReviewContentType contentType = type == null ? null : parseRequiredType(type);
        return reviewLogMapper.find(contentType, id, limit);
    }

    /**
     * 解析并校验审核内容类型。
     *
     * @param type 审核内容类型
     * @return 转换后的结果
     */
    private ReviewContentType parseRequiredType(String type) {
        ReviewContentType result = ReviewContentType.parse(type);
        if (result == null) {
            throw new BusinessException("INVALID_REVIEW_TYPE", "type must be PRODUCT or ACTIVITY");
        }
        return result;
    }

    /**
     * 创建审核日志基础信息。
     *
     * @param type 审核内容类型
     * @param id 记录主键
     * @param username 当前用户名
     * @param result 结果参数
     * @param reason 操作原因
     * @return 方法执行结果
     */
    private ReviewLogEntity baseLog(ReviewContentType type, Long id, String username,
                                    String result, String reason) {
        UserEntity reviewer = userMapper.findByUsername(username);
        ReviewLogEntity log = new ReviewLogEntity();
        log.setContentType(type);
        log.setContentId(id);
        log.setReviewerId(reviewer == null ? null : reviewer.getId());
        log.setReviewerUsername(username);
        log.setResult(result);
        log.setReason(reason);
        return log;
    }

    /**
     * 查询并校验商品。
     *
     * @param id 记录主键
     * @return 方法执行结果
     */
    private ProductEntity requireProduct(Long id) {
        ProductEntity product = productMapper.findById(id);
        if (product == null) throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        return product;
    }

    /**
     * 查询审核内容的提交人主键。
     *
     * @param userId 用户主键
     * @return 方法执行结果
     */
    private String submitter(Long userId) {
        if (userId == null) return null;
        UserEntity user = userMapper.findById(userId);
        return user == null ? null : user.getUsername();
    }

    /**
     * 查询并校验活动。
     *
     * @param id 记录主键
     * @return 方法执行结果
     */
    private FlashActivityEntity requireActivity(Long id) {
        FlashActivityEntity activity = activityMapper.findById(id);
        if (activity == null) throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        return activity;
    }
}
