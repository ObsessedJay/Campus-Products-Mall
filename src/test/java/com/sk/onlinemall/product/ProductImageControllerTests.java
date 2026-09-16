package com.sk.onlinemall.product;

import com.sk.onlinemall.security.JwtTokenService;
import com.sk.onlinemall.storage.ImageStorageService;
import com.sk.onlinemall.storage.UploadedImageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageControllerTests {
    private static final long PRODUCT_ID = 901L;
    private static final long CATEGORY_ID = 90L;
    private static final String OBJECT_NAME = "11111111-1111-1111-1111-111111111111.png";
    private static final String IMAGE_URL = "/api/v1/files/images/" + OBJECT_NAME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService tokenService;

    @MockBean
    private ImageStorageService imageStorageService;

    /**
     * 准备独立的商品图片测试数据。
     */
    @BeforeEach
    void setUpProduct() {
        cleanUpProduct();
        jdbcTemplate.update("INSERT INTO product_category (id, name, sort_order, status) VALUES (?, ?, 1, 'ACTIVE')",
                CATEGORY_ID, "Image Test Category");
        jdbcTemplate.update("""
                INSERT INTO product
                  (id, category_id, name, sale_type, status, price, stock, sold_count, limit_per_user)
                VALUES (?, ?, 'Image Test Product', 'NORMAL', 'DRAFT', 19.90, 10, 0, 1)
                """, PRODUCT_ID, CATEGORY_ID);
        when(imageStorageService.upload(any())).thenReturn(
                new UploadedImageResponse(OBJECT_NAME, IMAGE_URL, "image/png", 9));
    }

    /**
     * 清理独立的商品图片测试数据。
     */
    @AfterEach
    void tearDownProduct() {
        cleanUpProduct();
    }

    /**
     * 验证学生角色不能管理商品图片。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldRejectStudentImageUpload() throws Exception {
        String token = tokenService.issue(1L, "student", "STUDENT");
        MockMultipartFile file = pngFile();

        mockMvc.perform(multipart("/api/v1/admin/products/{productId}/images", PRODUCT_ID)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证运营人员可上传图片并自动设置首张封面。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldUploadImageAndSetFirstCover() throws Exception {
        String token = tokenService.issue(2L, "operator", "OPERATOR");

        mockMvc.perform(multipart("/api/v1/admin/products/{productId}/images", PRODUCT_ID)
                        .file(pngFile())
                        .param("displayName", "校园纪念封面")
                        .param("sortOrder", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.objectName").value(OBJECT_NAME))
                .andExpect(jsonPath("$.data.displayName").value("校园纪念封面"))
                .andExpect(jsonPath("$.data.originalName").doesNotExist())
                .andExpect(jsonPath("$.data.sortOrder").value(3));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cover_url FROM product WHERE id = ?", String.class, PRODUCT_ID)).isEqualTo(IMAGE_URL);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT original_name FROM product_image WHERE product_id = ?", String.class, PRODUCT_ID))
                .isEqualTo("cover.png");
    }

    /**
     * 验证草稿图库不公开且在售后可按顺序读取。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposeImagesOnlyForOnSaleProduct() throws Exception {
        insertImage(9101L, OBJECT_NAME, IMAGE_URL, 2);

        mockMvc.perform(get("/api/v1/products/{productId}/images", PRODUCT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", PRODUCT_ID);
        mockMvc.perform(get("/api/v1/products/{productId}/images", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].url").value(IMAGE_URL));
    }

    /**
     * 验证排序更新和删除封面后自动切换下一张图片。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldReorderAndReplaceDeletedCover() throws Exception {
        String secondObject = "22222222-2222-2222-2222-222222222222.jpg";
        String secondUrl = "/api/v1/files/images/" + secondObject;
        String token = tokenService.issue(2L, "operator", "OPERATOR");
        insertImage(9101L, OBJECT_NAME, IMAGE_URL, 0);
        insertImage(9102L, secondObject, secondUrl, 1);
        jdbcTemplate.update("UPDATE product SET cover_url = ? WHERE id = ?", IMAGE_URL, PRODUCT_ID);

        mockMvc.perform(put("/api/v1/admin/products/{productId}/images/{imageId}/sort", PRODUCT_ID, 9102L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(4));

        mockMvc.perform(delete("/api/v1/admin/products/{productId}/images/{imageId}", PRODUCT_ID, 9101L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cover_url FROM product WHERE id = ?", String.class, PRODUCT_ID)).isEqualTo(secondUrl);
        verify(imageStorageService).delete(OBJECT_NAME);
    }

    /**
     * 创建最小合法 PNG 测试文件。
     *
     * @return PNG 测试文件
     */
    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "cover.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01});
    }

    /**
     * 写入商品图片测试数据。
     *
     * @param id 图片主键
     * @param objectName 对象名称
     * @param url 图片地址
     * @param sortOrder 排序值
     */
    private void insertImage(long id, String objectName, String url, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO product_image
                  (id, product_id, object_name, url, content_type, size_bytes, sort_order)
                VALUES (?, ?, ?, ?, 'image/png', 9, ?)
                """, id, PRODUCT_ID, objectName, url, sortOrder);
    }

    /**
     * 删除本测试使用的商品与分类。
     */
    private void cleanUpProduct() {
        jdbcTemplate.update("DELETE FROM product_image WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM product_category WHERE id = ?", CATEGORY_ID);
    }
}
