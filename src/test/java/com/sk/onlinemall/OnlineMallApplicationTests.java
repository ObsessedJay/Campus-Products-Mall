package com.sk.onlinemall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OnlineMallApplicationTests {

    /**
     * 验证 Spring 应用上下文可以正常加载。
     */
    @Test
    void contextLoads() {
    }

}
