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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductFavoriteControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    private String studentToken;

    /**
     * 准备测试依赖和基础数据。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_favorite");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role, status) VALUES (701, 'favorite-student', 'unused', 'STUDENT', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO product_category (id, name, status) VALUES (701, 'Favorite Category', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO product (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (701, 701, 'Favorite Product', 'NORMAL', 'ON_SALE', 8.80, 10, 0, 1)
                """);
        studentToken = tokenService.issue(701L, "favorite-student", "STUDENT");
    }

    /**
     * 验证新增并移除收藏幂等性。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldAddAndRemoveFavoriteIdempotently() throws Exception {
        mockMvc.perform(post("/api/v1/products/701/favorite")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(701));

        mockMvc.perform(post("/api/v1/products/701/favorite")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/favorites")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Favorite Product"));

        mockMvc.perform(delete("/api/v1/products/701/favorite")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/favorites")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    /**
     * 验证收藏接口需要学生身份。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldProtectFavoriteEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/products/701/favorite"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/favorites"))
                .andExpect(status().isUnauthorized());
    }
}
