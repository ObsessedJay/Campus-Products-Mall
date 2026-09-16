package com.sk.onlinemall.product.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.mapper.ProductFavoriteMapper;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductFavoriteEntity;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductFavoriteService {
    private final ProductFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    /**
     * 创建 ProductFavoriteService 实例。
     *
     * @param favoriteMapper 收藏数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public ProductFavoriteService(ProductFavoriteMapper favoriteMapper, ProductMapper productMapper,
                                  UserMapper userMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询我的数据。
     *
     * @param username 当前用户名
     * @return 查询结果
     */
    public List<ProductEntity> findMine(String username) {
        return favoriteMapper.findProducts(requireUser(username).getId());
    }

    /**
     * 新增。
     *
     * @param productId 商品主键
     * @param username 当前用户名
     * @return 方法执行结果
     */
    @Transactional
    public ProductEntity add(Long productId, String username) {
        UserEntity user = requireUser(username);
        ProductEntity product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        if (favoriteMapper.find(user.getId(), productId) == null) {
            ProductFavoriteEntity favorite = new ProductFavoriteEntity();
            favorite.setUserId(user.getId());
            favorite.setProductId(productId);
            try {
                favoriteMapper.insert(favorite);
            } catch (DuplicateKeyException ignored) {
                // A concurrent identical request has already created the favorite.
            }
        }
        return product;
    }

    /**
     * 幂等取消当前用户的商品收藏。
     *
     * @param productId 商品主键
     * @param username 当前用户名
     */
    @Transactional
    public void remove(Long productId, String username) {
        favoriteMapper.delete(requireUser(username).getId(), productId);
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
}
