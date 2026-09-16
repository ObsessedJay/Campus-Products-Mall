package com.sk.onlinemall.system.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.system.mapper.SystemConfigMapper;
import com.sk.onlinemall.system.model.SystemConfigItem;
import com.sk.onlinemall.system.model.SystemConfigKey;
import com.sk.onlinemall.system.model.SystemConfigLog;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {
    private final SystemConfigMapper configMapper;
    private final UserMapper userMapper;

    /**
     * 创建系统配置服务。
     *
     * @param configMapper 配置数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public SystemConfigService(SystemConfigMapper configMapper, UserMapper userMapper) {
        this.configMapper = configMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询全部配置。
     *
     * @return 配置列表
     */
    public List<SystemConfigItem> findAll() {
        return configMapper.findAll();
    }

    /**
     * 获取整数配置，缺失时使用兜底值。
     *
     * @param key 配置键
     * @param fallback 兜底值
     * @return 当前整数值
     */
    public int getInt(SystemConfigKey key, int fallback) {
        SystemConfigItem item = configMapper.findByKey(key.name());
        return item == null ? fallback : key.parse(item.getConfigValue());
    }

    /**
     * 校验并即时更新配置。
     *
     * @param keyText 配置键文本
     * @param value 新值
     * @param reason 变更原因
     * @param username 当前管理员
     * @return 更新后的配置
     */
    @Transactional
    public SystemConfigItem update(String keyText, String value, String reason, String username) {
        UserEntity operator = requireAdmin(username);
        SystemConfigKey key;
        try {
            key = SystemConfigKey.valueOf(keyText.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("CONFIG_NOT_FOUND", "system config does not exist");
        }
        String normalized = Integer.toString(key.parse(value.trim()));
        SystemConfigItem current = configMapper.findByKey(key.name());
        if (current == null) {
            throw new BusinessException("CONFIG_NOT_FOUND", "system config does not exist");
        }
        if (current.getConfigValue().equals(normalized)) {
            return current;
        }
        if (configMapper.update(key.name(), current.getConfigValue(), normalized, operator.getId()) != 1) {
            throw new BusinessException("CONFIG_CHANGED", "system config changed, please retry");
        }
        configMapper.insertLog(key.name(), current.getConfigValue(), normalized, reason.trim(), operator.getId());
        return configMapper.findByKey(key.name());
    }

    /**
     * 查询配置变更日志。
     *
     * @param key 可选配置键
     * @return 变更记录
     */
    public List<SystemConfigLog> findLogs(String key) {
        String normalized = TextUtil.normalizeUppercaseToNull(key);
        if (normalized != null) {
            try {
                SystemConfigKey.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("CONFIG_NOT_FOUND", "system config does not exist");
            }
        }
        return configMapper.findLogs(normalized);
    }

    /**
     * 查询并校验管理员身份。
     *
     * @param username 当前用户名
     * @return 管理员用户
     */
    private UserEntity requireAdmin(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getRole() != UserRole.ADMIN) {
            throw new BusinessException("ADMIN_REQUIRED", "active administrator account is required");
        }
        return user;
    }
}
