package com.sk.onlinemall.review.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.order.mapper.TradeOrderMapper;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.review.dto.CreateProductReviewRequest;
import com.sk.onlinemall.review.mapper.ProductReviewMapper;
import com.sk.onlinemall.review.model.ProductReviewEntity;
import com.sk.onlinemall.review.model.ProductReviewStatus;
import com.sk.onlinemall.review.model.ProductReviewView;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductReviewService {
    private final ProductReviewMapper reviewMapper;
    private final TradeOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    /**
     * 创建商品评价服务。
     *
     * @param reviewMapper 评价数据访问组件
     * @param orderMapper 订单数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public ProductReviewService(ProductReviewMapper reviewMapper, TradeOrderMapper orderMapper,
                                ProductMapper productMapper, UserMapper userMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询商品公开评价并附加图片。
     *
     * @param productId 商品主键
     * @return 公开评价列表
     */
    public List<ProductReviewView> findVisible(Long productId) {
        if (productMapper.findById(productId) == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product not found");
        }
        return reviewMapper.findVisibleByProduct(productId).stream()
                .map(review -> new ProductReviewView(review.id(), review.productId(), review.nickname(),
                        review.rating(), review.content(), reviewMapper.findImageUrls(review.id()), review.createdAt()))
                .toList();
    }

    /**
     * 为已完成订单创建一次商品评价。
     *
     * @param orderId 订单主键
     * @param request 评价内容
     * @param username 当前用户名
     * @return 新增后的公开评价
     */
    @Transactional
    public ProductReviewView create(Long orderId, CreateProductReviewRequest request, String username) {
        UserEntity user = requireUser(username);
        if (orderMapper.countCompletedProduct(orderId, user.getId(), request.productId()) < 1) {
            throw new BusinessException("REVIEW_ORDER_NOT_ELIGIBLE", "only completed order items can be reviewed");
        }
        ProductReviewEntity existing = reviewMapper.findByOrderAndProduct(orderId, request.productId());
        if (existing != null) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "this order item has already been reviewed");
        }
        List<String> imageUrls = request.imageUrls() == null ? List.of() : request.imageUrls().stream()
                .map(String::trim).distinct().toList();
        if (imageUrls.size() > 3 || imageUrls.stream().anyMatch(url -> !url.startsWith("/api/v1/files/images/"))) {
            throw new BusinessException("INVALID_REVIEW_IMAGE", "review images must use uploaded image URLs");
        }
        ProductReviewEntity review = new ProductReviewEntity();
        review.setOrderId(orderId);
        review.setProductId(request.productId());
        review.setUserId(user.getId());
        review.setRating(request.rating());
        review.setContent(request.content().trim());
        review.setStatus(ProductReviewStatus.VISIBLE);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "this order item has already been reviewed");
        }
        for (int index = 0; index < imageUrls.size(); index++) {
            reviewMapper.insertImage(review.getId(), imageUrls.get(index), index);
        }
        ProductReviewEntity saved = reviewMapper.findById(review.getId());
        return new ProductReviewView(saved.getId(), saved.getProductId(), user.getNickname(), saved.getRating(),
                saved.getContent(), imageUrls, saved.getCreatedAt());
    }

    /**
     * 查询当前用户实体。
     *
     * @param username 当前用户名
     * @return 用户实体
     */
    private UserEntity requireUser(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "user not found");
        }
        return user;
    }
}
