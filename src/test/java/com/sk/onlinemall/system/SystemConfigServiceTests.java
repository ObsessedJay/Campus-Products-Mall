package com.sk.onlinemall.system;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.system.mapper.SystemConfigMapper;
import com.sk.onlinemall.system.model.SystemConfigItem;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.service.SystemConfigService;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemConfigServiceTests {
    /**
     * 验证配置值更新后立即可查询且写入审计。
     */
    @Test
    void shouldUpdateValidatedConfigAndWriteAudit() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SystemConfigService service = new SystemConfigService(mapper, userMapper);
        UserEntity admin = user(1L, UserRole.ADMIN, UserStatus.ACTIVE);
        SystemConfigItem before = item("15");
        SystemConfigItem after = item("30");
        when(userMapper.findByUsername("admin")).thenReturn(admin);
        when(mapper.findByKey("PAYMENT_TIMEOUT_MINUTES")).thenReturn(before, after);
        when(mapper.update("PAYMENT_TIMEOUT_MINUTES", "15", "30", 1L)).thenReturn(1);

        assertEquals("30", service.update("payment_timeout_minutes", "30", "运营调整", "admin")
                .getConfigValue());
        verify(mapper).insertLog("PAYMENT_TIMEOUT_MINUTES", "15", "30", "运营调整", 1L);
    }

    /**
     * 验证越界动态配置被拒绝。
     */
    @Test
    void shouldRejectOutOfRangeConfig() {
        assertThrows(BusinessException.class, () -> SystemConfigKey.PAYMENT_TIMEOUT_MINUTES.parse("0"));
    }

    /**
     * 创建测试用户。
     *
     * @param id 用户主键
     * @param role 用户角色
     * @param status 用户状态
     * @return 测试用户
     */
    private UserEntity user(Long id, UserRole role, UserStatus status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    /**
     * 创建测试配置项。
     *
     * @param value 配置值
     * @return 测试配置
     */
    private SystemConfigItem item(String value) {
        SystemConfigItem item = new SystemConfigItem();
        item.setConfigKey("PAYMENT_TIMEOUT_MINUTES");
        item.setConfigValue(value);
        return item;
    }
}
