package com.sk.onlinemall.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DigestUtil {

    /**
     * 禁止实例化摘要工具类。
     */
    private DigestUtil() {
    }

    /**
     * 计算文本的 SHA-256 十六进制摘要。
     *
     * @param value 原始文本
     * @return SHA-256 十六进制摘要
     */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
