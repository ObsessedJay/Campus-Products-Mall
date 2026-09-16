package com.sk.onlinemall.product;

import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCategoryManagementControllerTests {
    private static final long CATEGORY_ID = 93L;
    private static final long PRODUCT_ID = 903L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    /**
     * 准备分类管理测试数据。
     */
    @BeforeEach
    void setUpCategory() {
        cleanUpCategory();
        jdbcTemplate.update("INSERT INTO product_category (id, name, sort_order, status) VALUES (?, ?, 2, 'ACTIVE')",
                CATEGORY_ID, "Category Management Test");
    }

    /**
     * 清理分类管理测试数据。
     */
    @AfterEach
    void tearDownCategory() {
        cleanUpCategory();
    }

    /**
     * 验证学生不能访问分类管理接口。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectStudentCategoryManagement() throws Exception {
        String token = tokenService.issue(1L, "student", "STUDENT");
        mockMvc.perform(get("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证运营人员可创建、编辑、停用并删除空分类。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldManageCategoryLifecycle() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(put("/api/v1/admin/categories/{categoryId}", CATEGORY_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Category","sortOrder":8,"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Category"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 93)]").isEmpty());

        mockMvc.perform(delete("/api/v1/admin/categories/{categoryId}", CATEGORY_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_category WHERE id = ?", Integer.class, CATEGORY_ID)).isZero();
    }

    /**
     * 验证被商品引用的分类不能删除。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectDeletingCategoryUsedByProduct() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        jdbcTemplate.update("""
                INSERT INTO product
                  (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (?, ?, 'Category Reference Product', 'NORMAL', 'DRAFT', 10.00, 1, 0, 1)
                """, PRODUCT_ID, CATEGORY_ID);

        mockMvc.perform(delete("/api/v1/admin/categories/{categoryId}", CATEGORY_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    /**
     * 验证运营人员可通过管理接口新增分类。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldCreateCategoryFromManagementEndpoint() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Managed Category\",\"sortOrder\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        jdbcTemplate.update("DELETE FROM product_category WHERE name = 'New Managed Category'");
    }

    /**
     * 删除本测试使用的商品与分类。
     */
    private void cleanUpCategory() {
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM product_category WHERE id = ?", CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM product_category WHERE name = 'New Managed Category'");
    }
}
