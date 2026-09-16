package com.sk.onlinemall.user.model;

public enum UserRole {
    STUDENT,
    MERCHANT,
    OPERATOR,
    ADMIN;

    /**
     * 判断角色是否具有商家端身份。
     *
     * @return 新版商家角色或旧版运营角色时返回 true
     */
    public boolean isMerchant() {
        return this == MERCHANT || this == OPERATOR;
    }
}
