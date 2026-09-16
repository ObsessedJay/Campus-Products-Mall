package com.sk.onlinemall.product;

import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCatalogTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUpCatalog() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("INSERT INTO product_category (id, name, sort_order, status) VALUES (10, 'Badges', 1, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, sort_order, status) VALUES (20, 'Retired', 2, 'DISABLED')");
        insertProduct(101, 10, "Campus Badge", "Limited metal badge", "FLASH_SALE", "ON_SALE", "29.90", 18);
        insertProduct(102, 10, "School Notebook", "Ruled paper", "NORMAL", "ON_SALE", "12.50", 4);
        insertProduct(103, 10, "Hidden Draft", "Not public", "NORMAL", "DRAFT", "9.90", 0);
    }

    /**
     * 验证商品目录仅分页筛选在售商品。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldFilterAndPageOnlyOnSaleProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("keyword", "badge")
                        .param("categoryId", "10")
                        .param("saleType", "flash_sale")
                        .param("minPrice", "20")
                        .param("maxPrice", "30")
                        .param("sort", "priceDesc")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Campus Badge"));
    }

    /**
     * 验证公开详情不会暴露草稿商品。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldHideDraftProductDetails() throws Exception {
        mockMvc.perform(get("/api/v1/products/103"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    /**
     * 验证公开分类接口仅返回启用分类。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposeOnlyActiveCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Badges"));
    }

    /**
     * 验证创建分类需要运营角色。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRequireOperatorRoleToCreateCategory() throws Exception {
        String studentToken = tokenService.issue(1L, "student", "STUDENT");
        String operatorToken = tokenService.issue(2L, "operator", "OPERATOR");
        String body = """
                {"name":"Graduation Gifts","sortOrder":3}
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Graduation Gifts"))
                .andExpect(jsonPath("$.data.sortOrder").value(3));
    }

    /**
     * 验证创建商品时拒绝停用分类。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectProductWithInactiveCategory() throws Exception {
        String operatorToken = tokenService.issue(2L, "operator", "OPERATOR");
        String body = """
                {
                  "categoryId":20,
                  "pickupPointId":1,
                  "name":"Unavailable Product",
                  "saleType":"NORMAL",
                  "price":10.00,
                  "stock":1,
                  "limitPerUser":1
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    /**
     * 写入用于目录筛选测试的商品数据。
     *
     * @param id 记录主键
     * @param categoryId 分类主键
     * @param name 商品名称
     * @param subtitle 商品副标题
     * @param saleType 发售Type参数
     * @param status 业务状态或连接关闭状态
     * @param price 商品价格
     * @param soldCount 已售数量
     */
    private void insertProduct(int id, int categoryId, String name, String subtitle,
                               String saleType, String status, String price, int soldCount) {
        jdbcTemplate.update("""
                        INSERT INTO product
                        (id, category_id, name, subtitle, sale_type, status, price, stock, sold_count, limit_per_user)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 20, ?, 1)
                        """,
                id, categoryId, name, subtitle, saleType, status, new BigDecimal(price), soldCount);
    }
}
