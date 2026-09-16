package com.sk.onlinemall.search;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.mapper.ProductMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchAdminService {
    private final ProductMapper productMapper;
    private final ObjectProvider<ProductSearchGateway> searchGateway;

    /**
     * 创建商品索引管理服务。
     *
     * @param productMapper 商品数据访问组件
     * @param searchGateway 可选搜索网关
     */
    public ProductSearchAdminService(ProductMapper productMapper,
                                     ObjectProvider<ProductSearchGateway> searchGateway) {
        this.productMapper = productMapper;
        this.searchGateway = searchGateway;
    }

    /**
     * 全量重建商品索引。
     *
     * @return 索引商品数量
     */
    public int rebuild() {
        ProductSearchGateway gateway = searchGateway.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException("SEARCH_UNAVAILABLE", "Elasticsearch product search is not enabled");
        }
        var products = productMapper.findAllForIndex();
        gateway.rebuild(products);
        return products.size();
    }
}
