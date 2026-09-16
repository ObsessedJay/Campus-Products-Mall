package com.sk.onlinemall.search;

import com.sk.onlinemall.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/search-index")
public class ProductSearchAdminController {
    private final ProductSearchAdminService searchAdminService;

    /**
     * 创建商品索引控制器。
     *
     * @param searchAdminService 索引管理服务
     */
    public ProductSearchAdminController(ProductSearchAdminService searchAdminService) {
        this.searchAdminService = searchAdminService;
    }

    /**
     * 全量重建商品搜索索引。
     *
     * @return 索引数量
     */
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Integer>> rebuild() {
        return ApiResponse.success(Map.of("indexed", searchAdminService.rebuild()));
    }
}
