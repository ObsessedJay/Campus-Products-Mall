package com.sk.onlinemall.system.model;

import com.sk.onlinemall.common.exception.BusinessException;

/**
 * 定义可动态调整的系统配置及其整数范围。
 */
public enum SystemConfigKey {
    PAYMENT_TIMEOUT_MINUTES(1, 1440),
    DEFAULT_PURCHASE_LIMIT(1, 100),
    RESERVATION_LEAD_MINUTES(0, 10080),
    WRITE_RATE_LIMIT(1, 10000),
    FLASH_SALE_RATE_LIMIT(1, 1000),
    LOGIN_FAILURE_LIMIT(1, 100);

    private final int minimum;
    private final int maximum;

    /**
     * 创建配置定义。
     *
     * @param minimum 最小允许值
     * @param maximum 最大允许值
     */
    SystemConfigKey(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * 解析并校验配置值。
     *
     * @param value 原始配置值
     * @return 校验后的整数
     */
    public int parse(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    /**
     * 创建配置值非法异常。
     *
     * @return 业务异常
     */
    private BusinessException invalid() {
        return new BusinessException("INVALID_CONFIG_VALUE",
                name() + " must be between " + minimum + " and " + maximum);
    }
}
