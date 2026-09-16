package com.sk.onlinemall.product.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.dto.CreateCategoryRequest;
import com.sk.onlinemall.product.dto.UpdateCategoryRequest;
import com.sk.onlinemall.product.mapper.ProductCategoryMapper;
import com.sk.onlinemall.product.model.ProductCategoryEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCategoryService {
    private final ProductCategoryMapper categoryMapper;

    /**
     * 创建 ProductCategoryService 实例。
     *
     * @param categoryMapper 分类数据访问组件
     */
    public ProductCategoryService(ProductCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 查询启用的。
     *
     * @return 查询结果
     */
    public List<ProductCategoryEntity> findActive() {
        return categoryMapper.findActive();
    }

    /**
     * 查询运营侧全部商品分类。
     *
     * @return 全部分类
     */
    public List<ProductCategoryEntity> findAll() {
        return categoryMapper.findAll();
    }

    /**
     * 校验名称唯一性并创建商品分类。
     *
     * @param request 请求参数
     * @return 处理后的业务数据
     */
    @Transactional
    public ProductCategoryEntity create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryMapper.findByName(name) != null) {
            throw new BusinessException("CATEGORY_EXISTS", "category name is already used");
        }
        ProductCategoryEntity category = new ProductCategoryEntity();
        category.setName(name);
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setStatus("ACTIVE");
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("CATEGORY_EXISTS", "category name is already used");
        }
        return category;
    }

    /**
     * 更新商品分类名称、排序和启用状态。
     *
     * @param categoryId 分类主键
     * @param request 更新请求
     * @return 更新后的分类
     */
    @Transactional
    public ProductCategoryEntity update(Long categoryId, UpdateCategoryRequest request) {
        ProductCategoryEntity category = requireCategory(categoryId);
        String name = request.name().trim();
        ProductCategoryEntity sameName = categoryMapper.findByName(name);
        if (sameName != null && !sameName.getId().equals(categoryId)) {
            throw new BusinessException("CATEGORY_EXISTS", "category name is already used");
        }
        category.setName(name);
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setStatus(request.status());
        try {
            categoryMapper.update(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("CATEGORY_EXISTS", "category name is already used");
        }
        return categoryMapper.findById(categoryId);
    }

    /**
     * 删除没有商品引用的分类。
     *
     * @param categoryId 分类主键
     */
    @Transactional
    public void delete(Long categoryId) {
        requireCategory(categoryId);
        if (categoryMapper.countProducts(categoryId) > 0) {
            throw new BusinessException("CATEGORY_IN_USE", "categories used by products cannot be deleted");
        }
        if (categoryMapper.delete(categoryId) != 1) {
            throw new BusinessException("CATEGORY_STATE_CHANGED", "category state changed, please refresh and retry");
        }
    }

    /**
     * 查询并校验分类存在。
     *
     * @param categoryId 分类主键
     * @return 分类信息
     */
    private ProductCategoryEntity requireCategory(Long categoryId) {
        ProductCategoryEntity category = categoryMapper.findById(categoryId);
        if (category == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "category does not exist");
        }
        return category;
    }
}
