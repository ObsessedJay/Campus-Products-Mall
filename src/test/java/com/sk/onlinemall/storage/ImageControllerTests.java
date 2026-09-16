package com.sk.onlinemall.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageControllerTests {
    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证公开图片读取接口在鉴权前执行对象名校验。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldExposePublicImageDownload() throws Exception {
        mockMvc.perform(get("/api/v1/files/images/invalid.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OBJECT_NAME"));
    }

    /**
     * 验证匿名用户不能上传图片。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldProtectImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/files/images").file(file))
                .andExpect(status().isUnauthorized());
    }
}
