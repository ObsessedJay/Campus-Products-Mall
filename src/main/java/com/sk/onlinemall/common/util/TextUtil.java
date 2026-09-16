package com.sk.onlinemall.common.util;

import java.util.Locale;

public final class TextUtil {

    /**
     * 禁止实例化文本工具类。
     */
    private TextUtil() {
    }

    /**
     * 去除文本首尾空白并转换为小写。
     *
     * @param value 原始文本
     * @return 规范化文本，原值为空时返回空字符串
     */
    public static String normalizeLowercase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 去除文本首尾空白并将空白内容转换为空值。
     *
     * @param value 原始文本
     * @return 去除空白后的文本，无有效内容时返回 null
     */
    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 去除文本首尾空白、转换为大写并将空白内容转换为空值。
     *
     * @param value 原始文本
     * @return 大写规范化文本，无有效内容时返回 null
     */
    public static String normalizeUppercaseToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 将文本截断到指定最大长度。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本，原值为空时返回 null
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
