package com.sk.onlinemall.user.service;

import com.sk.onlinemall.common.util.DigestUtil;
import com.sk.onlinemall.user.model.UserRole;

public final class AccountIdentity {

    /**
     * 禁止实例化账号标识工具类。
     */
    private AccountIdentity() {
    }

    /**
     * 根据规范化邮箱和角色生成稳定且不暴露邮箱原文的内部标识。
     *
     * @param email 规范化邮箱地址
     * @param role 账号角色
     * @return 内部账号标识
     */
    public static String fromEmailAndRole(String email, UserRole role) {
        return "mail_" + DigestUtil.sha256Hex(role.name() + ":" + email).substring(0, 40);
    }
}
