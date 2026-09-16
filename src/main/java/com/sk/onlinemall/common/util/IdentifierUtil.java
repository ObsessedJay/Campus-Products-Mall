package com.sk.onlinemall.common.util;

import java.util.UUID;

public final class IdentifierUtil {

    /**
     * 禁止实例化标识生成工具类。
     */
    private IdentifierUtil() {
    }

    /**
     * 生成不包含连字符的 UUID 文本。
     *
     * @return 32 位紧凑 UUID 文本
     */
    public static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
