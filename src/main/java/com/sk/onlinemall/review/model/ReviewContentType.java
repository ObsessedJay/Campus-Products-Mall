package com.sk.onlinemall.review.model;

public enum ReviewContentType {
    PRODUCT,
    ACTIVITY;

    /**
     * 解析。
     *
     * @param value 待处理文本
     * @return 转换后的结果
     */
    public static ReviewContentType parse(String value) {
        try {
            return value == null ? null : valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new com.sk.onlinemall.common.exception.BusinessException(
                    "INVALID_REVIEW_TYPE", "type must be PRODUCT or ACTIVITY");
        }
    }
}
