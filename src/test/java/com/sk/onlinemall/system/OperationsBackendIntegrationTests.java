package com.sk.onlinemall.system;

import com.sk.onlinemall.dashboard.service.DashboardService;
import com.sk.onlinemall.storage.StorageObjectMapper;
import com.sk.onlinemall.system.mapper.SystemConfigMapper;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class OperationsBackendIntegrationTests {
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SystemConfigMapper systemConfigMapper;
    @Autowired
    private StorageObjectMapper storageObjectMapper;

    /**
     * 验证新增运营模块的核心查询可在测试数据库执行。
     */
    @Test
    void shouldExecuteOperationsQueries() {
        assertDoesNotThrow(() -> dashboardService.metrics(
                LocalDate.now().minusDays(30), LocalDate.now(), null, null));
        assertDoesNotThrow(() -> userMapper.search(null, null, 0, 20));
        assertFalse(systemConfigMapper.findAll().isEmpty());
        assertDoesNotThrow(() -> storageObjectMapper.findOrphans(LocalDateTime.now()));
    }
}
