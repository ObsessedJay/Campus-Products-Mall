package com.sk.onlinemall.product.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.dto.UpsertProductSkuRequest;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.mapper.ProductSkuMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.model.ProductSkuEntity;
import com.sk.onlinemall.product.model.ProductStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProductSkuService {
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;

    /**
     * 创建商品规格服务。
     *
     * @param skuMapper 规格数据访问组件
     * @param productMapper 商品数据访问组件
     */
    public ProductSkuService(ProductSkuMapper skuMapper, ProductMapper productMapper) {
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
    }

    /**
     * 查询运营端商品规格。
     *
     * @param productId 商品主键
     * @return 全部规格
     */
    @Transactional(readOnly = true)
    public List<ProductSkuEntity> findForManagement(Long productId) {
        requireProduct(productId);
        return skuMapper.findByProductId(productId);
    }

    /**
     * 查询公开商品的启用规格。
     *
     * @param productId 商品主键
     * @return 启用规格
     */
    @Transactional(readOnly = true)
    public List<ProductSkuEntity> findPublic(Long productId) {
        ProductEntity product = requireProduct(productId);
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "product does not exist");
        }
        return skuMapper.findEnabledByProductId(productId);
    }

    /**
     * 新增商品规格并同步汇总库存。
     *
     * @param productId 商品主键
     * @param request 规格请求
     * @return 新增规格
     */
    @Transactional
    public ProductSkuEntity create(Long productId, UpsertProductSkuRequest request) {
        requireProduct(productId);
        ProductSkuEntity sku = fromRequest(productId, request);
        try {
            skuMapper.insert(sku);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("SKU_CODE_DUPLICATE", "skuCode already exists for this product");
        }
        syncProductAfterMutation(productId, true, false);
        return skuMapper.findById(sku.getId());
    }

    /**
     * 更新商品规格并同步汇总库存。
     *
     * @param productId 商品主键
     * @param skuId 规格主键
     * @param request 规格请求
     * @return 更新后规格
     */
    @Transactional
    public ProductSkuEntity update(Long productId, Long skuId, UpsertProductSkuRequest request) {
        requireProduct(productId);
        ProductSkuEntity existing = requireOwnedSku(productId, skuId);
        boolean contentChanged = !Objects.equals(existing.getSkuCode(), request.skuCode().trim())
                || !Objects.equals(existing.getName(), request.name().trim())
                || existing.getPrice().compareTo(request.price()) != 0
                || !Objects.equals(existing.getEnabled(), request.enabled());
        ProductSkuEntity sku = fromRequest(productId, request);
        sku.setId(skuId);
        try {
            skuMapper.update(sku);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("SKU_CODE_DUPLICATE", "skuCode already exists for this product");
        }
        syncProductAfterMutation(productId, contentChanged, false);
        return skuMapper.findById(skuId);
    }

    /**
     * 删除商品规格并同步汇总库存。
     *
     * @param productId 商品主键
     * @param skuId 规格主键
     */
    @Transactional
    public void delete(Long productId, Long skuId) {
        requireProduct(productId);
        requireOwnedSku(productId, skuId);
        if (skuMapper.countRestorableOrderItems(skuId) > 0) {
            throw new BusinessException("SKU_IN_USE", "product sku is referenced by an active order");
        }
        if (skuMapper.delete(skuId, productId) != 1) {
            throw new BusinessException("SKU_NOT_FOUND", "product sku does not exist");
        }
        syncProductAfterMutation(productId, true, true);
    }

    /**
     * 将请求转换为规格实体。
     *
     * @param productId 商品主键
     * @param request 规格请求
     * @return 规格实体
     */
    private ProductSkuEntity fromRequest(Long productId, UpsertProductSkuRequest request) {
        ProductSkuEntity sku = new ProductSkuEntity();
        sku.setProductId(productId);
        sku.setSkuCode(request.skuCode().trim());
        sku.setName(request.name().trim());
        sku.setPrice(request.price());
        sku.setStock(request.stock());
        sku.setEnabled(request.enabled());
        sku.setSortOrder(request.sortOrder());
        return sku;
    }

    /**
     * 校验并查询商品。
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
     * 校验规格归属关系。
     *
     * @param productId 商品主键
     * @param skuId 规格主键
     * @return 规格信息
     */
    private ProductSkuEntity requireOwnedSku(Long productId, Long skuId) {
        ProductSkuEntity sku = skuMapper.findById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BusinessException("SKU_NOT_FOUND", "product sku does not exist");
        }
        return sku;
    }

    /**
     * 同步商品汇总库存和审核状态。
     *
     * @param productId 商品主键
     * @param contentChanged 是否发生内容变化
     * @param keepStockWhenEmpty 删除最后一个规格时是否保留商品库存
     */
    private void syncProductAfterMutation(Long productId, boolean contentChanged, boolean keepStockWhenEmpty) {
        int count = skuMapper.countByProductId(productId);
        Integer stock = keepStockWhenEmpty && count == 0 ? null : skuMapper.sumEnabledStock(productId);
        productMapper.syncSkuSummary(productId, stock, contentChanged);
    }
}
