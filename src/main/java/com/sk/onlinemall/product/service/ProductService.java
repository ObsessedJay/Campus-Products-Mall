package com.sk.onlinemall.product.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.product.dto.CreateProductRequest;
import com.sk.onlinemall.product.dto.UpdateProductRequest;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.mapper.ProductSkuMapper;
import com.sk.onlinemall.product.mapper.ProductCategoryMapper;
import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.product.model.ProductSaleType;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointStatus;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;
import com.sk.onlinemall.search.ProductSearchGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.service.SystemConfigService;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final PickupPointMapper pickupPointMapper;
    private final UserMapper userMapper;
    private final ObjectProvider<ProductSearchGateway> searchGateway;
    private final SystemConfigService systemConfigService;

    /**
     * 创建 ProductService 实例。
     *
     * @param productMapper 商品数据访问组件
     * @param skuMapper 商品规格数据访问组件
     * @param categoryMapper 分类数据访问组件
     * @param pickupPointMapper 自提点数据访问组件
     * @param userMapper 用户数据访问组件
     * @param searchGateway 可选商品搜索网关
     * @param systemConfigService 系统动态配置服务
     */
    public ProductService(ProductMapper productMapper, ProductSkuMapper skuMapper,
                          ProductCategoryMapper categoryMapper, PickupPointMapper pickupPointMapper,
                          UserMapper userMapper,
                          ObjectProvider<ProductSearchGateway> searchGateway,
                          SystemConfigService systemConfigService) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.userMapper = userMapper;
        this.searchGateway = searchGateway;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 分页查询符合条件的在售商品。
     *
     * @param keyword 商品搜索关键词
     * @param categoryId 分类主键
     * @param saleType 发售Type参数
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sort 排序方式
     * @param page 页码
     * @param size 每页数量
     * @return 查询结果
     */
    public PageResponse<ProductEntity> findOnSale(String keyword, Long categoryId, String saleType,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   String sort, int page, int size) {
        String normalizedKeyword = TextUtil.trimToNull(keyword);
        if (page < 1) {
            throw new BusinessException("INVALID_PAGE", "page must be greater than zero");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and 100");
        }
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new BusinessException("INVALID_PRICE_RANGE", "price cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("INVALID_PRICE_RANGE", "minPrice cannot exceed maxPrice");
        }
        String normalizedSaleType = TextUtil.normalizeUppercaseToNull(saleType);
        if (normalizedSaleType != null) {
            try {
                ProductSaleType.valueOf(normalizedSaleType);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_SALE_TYPE", "saleType is not supported");
            }
        }
        String normalizedSort = switch (sort == null ? "latest" : sort) {
            case "latest", "priceAsc", "priceDesc", "sales" -> sort == null ? "latest" : sort;
            default -> throw new BusinessException("INVALID_SORT", "sort is not supported");
        };
        ProductSearchGateway gateway = searchGateway.getIfAvailable();
        if (gateway != null) {
            try {
                return gateway.search(normalizedKeyword, categoryId, normalizedSaleType,
                        minPrice, maxPrice, normalizedSort, page, size);
            } catch (RuntimeException exception) {
                log.warn("Elasticsearch query failed, falling back to MySQL", exception);
            }
        }
        int offset = (page - 1) * size;
        List<ProductEntity> items = productMapper.findOnSale(
                normalizedKeyword, categoryId, normalizedSaleType, minPrice, maxPrice,
                normalizedSort, offset, size);
        long total = productMapper.countOnSale(normalizedKeyword, categoryId, normalizedSaleType, minPrice, maxPrice);
        return PageResponse.of(items, total, page, size);
    }

    /**
     * 分页查询运营侧全部状态商品。
     *
     * @param keyword 商品搜索关键词
     * @param status 商品状态
     * @param page 页码
     * @param size 每页数量
     * @return 运营商品分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductEntity> findForManagement(String keyword, String status, int page, int size) {
        if (page < 1) {
            throw new BusinessException("INVALID_PAGE", "page must be greater than zero");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and 100");
        }
        String normalizedKeyword = TextUtil.trimToNull(keyword);
        String normalizedStatus = TextUtil.normalizeUppercaseToNull(status);
        if (normalizedStatus != null) {
            try {
                ProductStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_PRODUCT_STATUS", "product status is not supported");
            }
        }
        int offset = (page - 1) * size;
        List<ProductEntity> items = productMapper.findForManagement(
                normalizedKeyword, normalizedStatus, offset, size);
        long total = productMapper.countForManagement(normalizedKeyword, normalizedStatus);
        return PageResponse.of(items, total, page, size);
    }

    /**
     * 查询查询详情。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    public ProductEntity findDetail(Long id) {
        ProductEntity product = productMapper.findById(id);
        if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        return product;
    }

    /**
     * 使用系统默认操作人创建文创商品。
     *
     * @param request 请求参数
     * @return 处理后的业务数据
     */
    @Transactional
    public ProductEntity create(CreateProductRequest request) {
        return create(request, null);
    }

    /**
     * 校验分类并创建文创商品。
     *
     * @param request 请求参数
     * @param username 当前用户名
     * @return 处理后的业务数据
     */
    @Transactional
    public ProductEntity create(CreateProductRequest request, String username) {
        validateCategory(request.categoryId());
        validatePickupPoint(request.pickupPointId());
        ProductEntity product = new ProductEntity();
        product.setCategoryId(request.categoryId());
        product.setPickupPointId(request.pickupPointId());
        if (username != null) {
            var user = userMapper.findByUsername(username);
            product.setCreatedBy(user == null ? null : user.getId());
        }
        product.setName(request.name());
        product.setSubtitle(request.subtitle());
        product.setDescription(request.description());
        product.setCoverUrl(request.coverUrl());
        product.setSaleType(request.saleType());
        product.setStatus(ProductStatus.PENDING_REVIEW);
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setSoldCount(0);
        product.setLimitPerUser(request.limitPerUser() == null
                ? systemConfigService.getInt(SystemConfigKey.DEFAULT_PURCHASE_LIMIT, 1)
                : request.limitPerUser());
        productMapper.insert(product);
        return product;
    }

    /**
     * 更新商品资料、库存和限购配置。
     *
     * @param productId 商品主键
     * @param request 更新请求
     * @return 更新后的商品
     */
    @Transactional
    public ProductEntity update(Long productId, UpdateProductRequest request) {
        ProductEntity product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        validateCategory(request.categoryId());
        validatePickupPoint(request.pickupPointId());
        boolean skuManaged = skuMapper.countByProductId(productId) > 0;
        if (skuManaged && !Objects.equals(product.getStock(), request.stock())) {
            throw new BusinessException("SKU_STOCK_MANAGED", "product stock is managed by its skus");
        }
        String name = request.name().trim();
        String subtitle = TextUtil.trimToNull(request.subtitle());
        String description = TextUtil.trimToNull(request.description());
        boolean contentChanged = !Objects.equals(product.getCategoryId(), request.categoryId())
                || !Objects.equals(product.getPickupPointId(), request.pickupPointId())
                || !Objects.equals(product.getName(), name)
                || !Objects.equals(product.getSubtitle(), subtitle)
                || !Objects.equals(product.getDescription(), description)
                || product.getSaleType() != request.saleType()
                || product.getPrice().compareTo(request.price()) != 0;
        product.setCategoryId(request.categoryId());
        product.setPickupPointId(request.pickupPointId());
        product.setName(name);
        product.setSubtitle(subtitle);
        product.setDescription(description);
        product.setSaleType(request.saleType());
        product.setPrice(request.price());
        product.setStock(skuManaged ? product.getStock() : request.stock());
        product.setLimitPerUser(request.limitPerUser());
        if (contentChanged) {
            product.setStatus(ProductStatus.PENDING_REVIEW);
        }
        productMapper.updateForManagement(product);
        return productMapper.findById(productId);
    }

    /**
     * 将在售商品下架。
     *
     * @param productId 商品主键
     * @return 下架后的商品
     */
    @Transactional
    public ProductEntity takeOffSale(Long productId) {
        return changeStatus(productId, ProductStatus.ON_SALE, ProductStatus.OFF_SALE,
                "PRODUCT_NOT_ON_SALE", "only on-sale products can be taken off sale");
    }

    /**
     * 将未改动的下架商品重新上架。
     *
     * @param productId 商品主键
     * @return 上架后的商品
     */
    @Transactional
    public ProductEntity putOnSale(Long productId) {
        ProductEntity product = requireProduct(productId);
        validatePickupPoint(product.getPickupPointId());
        return changeStatus(productId, ProductStatus.OFF_SALE, ProductStatus.ON_SALE,
                "PRODUCT_NOT_OFF_SALE", "only off-sale products can be put on sale");
    }

    /**
     * 删除未公开且没有订单或活动历史的商品。
     *
     * @param productId 商品主键
     */
    @Transactional
    public void delete(Long productId) {
        ProductEntity product = requireProduct(productId);
        if (product.getStatus() == ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_ON_SALE", "on-sale products must be taken off sale first");
        }
        if (productMapper.countOrderItems(productId) > 0) {
            throw new BusinessException("PRODUCT_HAS_ORDERS", "products with order history cannot be deleted");
        }
        if (productMapper.countActivities(productId) > 0) {
            throw new BusinessException("PRODUCT_HAS_ACTIVITIES", "products used by activities cannot be deleted");
        }
        productMapper.deleteFavorites(productId);
        if (productMapper.delete(productId) != 1) {
            throw new BusinessException("PRODUCT_STATE_CHANGED", "product state changed, please refresh and retry");
        }
    }

    /**
     * 按预期状态执行商品生命周期转换。
     *
     * @param productId 商品主键
     * @param expectedStatus 预期当前状态
     * @param targetStatus 目标状态
     * @param errorCode 非法状态错误码
     * @param errorMessage 非法状态错误信息
     * @return 更新后的商品
     */
    private ProductEntity changeStatus(Long productId, ProductStatus expectedStatus, ProductStatus targetStatus,
                                        String errorCode, String errorMessage) {
        ProductEntity product = requireProduct(productId);
        if (product.getStatus() != expectedStatus) {
            throw new BusinessException(errorCode, errorMessage);
        }
        if (productMapper.updateStatusIfCurrent(productId, targetStatus, expectedStatus) != 1) {
            throw new BusinessException("PRODUCT_STATE_CHANGED", "product state changed, please refresh and retry");
        }
        return productMapper.findById(productId);
    }

    /**
     * 查询并校验商品存在。
     *
     * @param productId 商品主键
     * @return 商品信息
     */
    private ProductEntity requireProduct(Long productId) {
        ProductEntity product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        return product;
    }

    /**
     * 校验可选商品分类处于启用状态。
     *
     * @param categoryId 分类主键
     */
    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        var category = categoryMapper.findById(categoryId);
        if (category == null || !"ACTIVE".equals(category.getStatus())) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "category does not exist");
        }
    }

    /**
     * 校验商品领取点存在且处于启用状态。
     *
     * @param pickupPointId 领取点主键
     */
    private void validatePickupPoint(Long pickupPointId) {
        var pickupPoint = pickupPointMapper.findById(pickupPointId);
        if (pickupPoint == null || pickupPoint.getStatus() != PickupPointStatus.ACTIVE) {
            throw new BusinessException("PICKUP_POINT_NOT_AVAILABLE", "product pickup point is not active");
        }
    }

}
