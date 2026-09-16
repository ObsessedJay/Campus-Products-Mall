package com.sk.onlinemall.product.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.common.util.TransactionCallbackUtil;
import com.sk.onlinemall.product.mapper.ProductImageMapper;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductImageEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import com.sk.onlinemall.storage.ImageStorageService;
import com.sk.onlinemall.storage.UploadedImageResponse;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductImageMapper imageMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final ImageStorageService imageStorageService;
    private final int maxImages;

    /**
     * 创建商品图片服务。
     *
     * @param imageMapper 商品图片数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     * @param imageStorageService 图片存储服务
     * @param maxImages 单个商品允许的最大图片数
     */
    public ProductImageService(
            ProductImageMapper imageMapper,
            ProductMapper productMapper,
            UserMapper userMapper,
            ImageStorageService imageStorageService,
            @Value("${app.product.max-images:8}") int maxImages) {
        this.imageMapper = imageMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.imageStorageService = imageStorageService;
        this.maxImages = maxImages;
    }

    /**
     * 查询公开在售商品的图片列表。
     *
     * @param productId 商品主键
     * @return 商品图片列表
     */
    @Transactional(readOnly = true)
    public List<ProductImageEntity> findPublic(Long productId) {
        ProductEntity product = requireProduct(productId);
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        return imageMapper.findByProductId(productId);
    }

    /**
     * 查询运营侧商品图片列表。
     *
     * @param productId 商品主键
     * @return 商品图片列表
     */
    @Transactional(readOnly = true)
    public List<ProductImageEntity> findForManagement(Long productId) {
        requireProduct(productId);
        return imageMapper.findByProductId(productId);
    }

    /**
     * 上传图片并绑定到指定商品。
     *
     * @param productId 商品主键
     * @param file 图片文件
     * @param displayName 商家设置的图片展示名称
     * @param sortOrder 可选排序值
     * @param username 当前用户名
     * @return 新增商品图片
     */
    @Transactional
    public ProductImageEntity upload(Long productId, MultipartFile file, String displayName,
                                     Integer sortOrder, String username) {
        ProductEntity product = requireProduct(productId);
        if (imageMapper.countByProductId(productId) >= maxImages) {
            throw new BusinessException("PRODUCT_IMAGE_LIMIT_EXCEEDED", "product image limit has been reached");
        }
        UploadedImageResponse uploaded = imageStorageService.upload(file);
        try {
            ProductImageEntity image = new ProductImageEntity();
            image.setProductId(productId);
            image.setObjectName(uploaded.objectName());
            image.setDisplayName(normalizeDisplayName(displayName));
            image.setOriginalName(normalizeOriginalName(file.getOriginalFilename()));
            image.setUrl(uploaded.url());
            image.setContentType(uploaded.contentType());
            image.setSizeBytes(uploaded.size());
            image.setSortOrder(sortOrder == null ? imageMapper.nextSortOrder(productId) : validateSort(sortOrder));
            var user = userMapper.findByUsername(username);
            image.setCreatedBy(user == null ? null : user.getId());
            imageMapper.insert(image);
            if (product.getCoverUrl() == null || product.getCoverUrl().isBlank()) {
                productMapper.updateCoverUrl(productId, image.getUrl());
            }
            return image;
        } catch (RuntimeException exception) {
            safeDeleteObject(uploaded.objectName());
            throw exception;
        }
    }

    /**
     * 调整商品图片展示顺序。
     *
     * @param productId 商品主键
     * @param imageId 图片主键
     * @param sortOrder 排序值
     * @return 更新后的商品图片
     */
    @Transactional
    public ProductImageEntity updateSortOrder(Long productId, Long imageId, int sortOrder) {
        requireProduct(productId);
        ProductImageEntity image = requireImage(productId, imageId);
        imageMapper.updateSortOrder(imageId, productId, validateSort(sortOrder));
        image.setSortOrder(sortOrder);
        return image;
    }

    /**
     * 删除商品图片并在事务提交后清理对象存储。
     *
     * @param productId 商品主键
     * @param imageId 图片主键
     */
    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductEntity product = requireProduct(productId);
        ProductImageEntity image = requireImage(productId, imageId);
        imageMapper.delete(imageId, productId);
        if (image.getUrl().equals(product.getCoverUrl())) {
            List<ProductImageEntity> remaining = imageMapper.findByProductId(productId);
            productMapper.updateCoverUrl(productId, remaining.isEmpty() ? null : remaining.get(0).getUrl());
        }
        deleteObjectAfterCommit(image.getObjectName());
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
     * 查询并校验商品图片存在。
     *
     * @param productId 商品主键
     * @param imageId 图片主键
     * @return 商品图片
     */
    private ProductImageEntity requireImage(Long productId, Long imageId) {
        ProductImageEntity image = imageMapper.findByIdAndProductId(imageId, productId);
        if (image == null) {
            throw new BusinessException("PRODUCT_IMAGE_NOT_FOUND", "product image does not exist");
        }
        return image;
    }

    /**
     * 校验图片排序值。
     *
     * @param sortOrder 排序值
     * @return 合法排序值
     */
    private int validateSort(int sortOrder) {
        if (sortOrder < 0) {
            throw new BusinessException("INVALID_IMAGE_SORT", "image sort order cannot be negative");
        }
        return sortOrder;
    }

    /**
     * 规范化并校验商家设置的图片展示名称。
     *
     * @param displayName 原始展示名称
     * @return 规范化名称，未填写时返回 null
     */
    private String normalizeDisplayName(String displayName) {
        String normalized = TextUtil.trimToNull(displayName);
        if (normalized != null && normalized.length() > 100) {
            throw new BusinessException("INVALID_IMAGE_NAME", "image display name cannot exceed 100 characters");
        }
        return normalized;
    }

    /**
     * 提取安全的原始文件名称用于内部追溯。
     *
     * @param originalName 客户端原始文件名
     * @return 去除路径和控制字符后的文件名
     */
    private String normalizeOriginalName(String originalName) {
        String normalized = TextUtil.trimToNull(originalName);
        if (normalized == null) return null;
        normalized = normalized.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        normalized = normalized.chars()
                .filter(character -> !Character.isISOControl(character))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return TextUtil.truncate(TextUtil.trimToNull(normalized), 255);
    }

    /**
     * 在当前事务成功提交后删除对象。
     *
     * @param objectName 对象名称
     */
    private void deleteObjectAfterCommit(String objectName) {
        TransactionCallbackUtil.afterCommit(() -> safeDeleteObject(objectName));
    }

    /**
     * 尝试删除对象并记录待后续清理的孤儿文件。
     *
     * @param objectName 对象名称
     */
    private void safeDeleteObject(String objectName) {
        try {
            imageStorageService.delete(objectName);
        } catch (RuntimeException exception) {
            log.error("Failed to delete product image object {}", objectName, exception);
        }
    }
}
