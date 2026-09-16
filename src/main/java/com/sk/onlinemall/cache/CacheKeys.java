package com.sk.onlinemall.cache;

public final class CacheKeys {
    private static final String PREFIX = "campus-creative:";

    /**
     * 禁止实例化缓存键工具类。
     */
    private CacheKeys() {
    }

    /**
     * 生成抢购活动库存键。
     *
     * @param activitySkuId 活动商品标识
     * @return Redis 库存键
     */
    public static String flashSaleStock(long activitySkuId) {
        return PREFIX + "flash-sale:stock:" + activitySkuId;
    }

    /**
     * 生成活动用户累计购买数量键。
     *
     * @param activitySkuId 活动商品标识
     * @param userId 用户标识
     * @return Redis 用户购买数量键
     */
    public static String flashSaleBuyer(long activitySkuId, long userId) {
        return PREFIX + "flash-sale:buyer:" + activitySkuId + ":" + userId;
    }

    /**
     * 生成活动已购买用户集合键。
     *
     * @param activityId 活动标识
     * @return Redis 用户集合键
     */
    public static String flashSaleBuyers(long activityId) {
        return PREFIX + "flash-sale:buyers:" + activityId;
    }

    /**
     * 生成抢购活动状态键。
     *
     * @param activityId 活动标识
     * @return Redis 活动状态键
     */
    public static String flashSaleState(long activityId) {
        return PREFIX + "flash-sale:state:" + activityId;
    }

    /**
     * 生成抢购请求幂等状态键。
     *
     * @param requestNo 请求编号
     * @return Redis 请求状态键
     */
    public static String flashSaleRequest(String requestNo) {
        return PREFIX + "flash-sale:request:" + requestNo;
    }

    /**
     * 生成活动库存校准锁键。
     *
     * @param activityId 活动标识
     * @return Redis 校准锁键
     */
    public static String flashSaleReconciliationLock(long activityId) {
        return PREFIX + "flash-sale:reconciliation-lock:" + activityId;
    }

    /**
     * 生成图形验证码键。
     *
     * @param captchaId 验证码标识
     * @return Redis 图形验证码键
     */
    public static String captcha(String captchaId) {
        return PREFIX + "auth:captcha:" + captchaId;
    }

    /**
     * 生成邮箱验证码键。
     *
     * @param email 邮箱地址
     * @return Redis 邮箱验证码键
     */
    public static String emailCode(String email) {
        return PREFIX + "auth:email-code:" + email;
    }

    /**
     * 生成邮箱验证码发送冷却键。
     *
     * @param email 邮箱地址
     * @return Redis 发送冷却键
     */
    public static String emailCodeCooldown(String email) {
        return PREFIX + "auth:email-code-cooldown:" + email;
    }

    /**
     * 生成商家注册邮箱验证码键。
     *
     * @param email 邮箱地址
     * @return 商家注册验证码键
     */
    public static String merchantEmailCode(String email) {
        return PREFIX + "auth:merchant-email-code:" + email;
    }

    /**
     * 生成商家注册验证码发送冷却键。
     *
     * @param email 邮箱地址
     * @return 商家注册验证码冷却键
     */
    public static String merchantEmailCodeCooldown(String email) {
        return PREFIX + "auth:merchant-email-code-cooldown:" + email;
    }

    /**
     * 生成找回密码邮箱验证码键。
     *
     * @param email 邮箱地址
     * @param role 账号入口角色
     * @return 找回密码验证码键
     */
    public static String passwordResetEmailCode(String email, String role) {
        return PREFIX + "auth:password-reset-code:" + role.toLowerCase() + ":" + email;
    }

    /**
     * 生成找回密码验证码发送冷却键。
     *
     * @param email 邮箱地址
     * @param role 账号入口角色
     * @return 找回密码发送冷却键
     */
    public static String passwordResetEmailCodeCooldown(String email, String role) {
        return PREFIX + "auth:password-reset-code-cooldown:" + role.toLowerCase() + ":" + email;
    }

    /**
     * 生成刷新令牌摘要键。
     *
     * @param tokenDigest 刷新令牌摘要
     * @return 刷新令牌缓存键
     */
    public static String refreshToken(String tokenDigest) {
        return PREFIX + "auth:refresh-token:" + tokenDigest;
    }

    /**
     * 生成用户刷新会话集合键。
     *
     * @param userId 用户标识
     * @return 用户刷新会话集合键
     */
    public static String userRefreshTokens(long userId) {
        return PREFIX + "auth:user-refresh-tokens:" + userId;
    }

    /**
     * 生成请求限流计数键。
     *
     * @param bucket 限流策略分组
     * @param fingerprint 客户端或用户指纹
     * @return Redis 限流计数键
     */
    public static String requestRate(String bucket, String fingerprint) {
        return PREFIX + "risk:rate:" + bucket + ":" + fingerprint;
    }

    /**
     * 生成登录失败计数键。
     *
     * @param scope 统计维度
     * @param fingerprint IP 或用户名指纹
     * @return Redis 登录失败计数键
     */
    public static String loginFailure(String scope, String fingerprint) {
        return PREFIX + "risk:login-failure:" + scope + ":" + fingerprint;
    }

    /**
     * 生成临时黑名单键。
     *
     * @param scope 黑名单维度
     * @param fingerprint IP 或用户名指纹
     * @return Redis 临时黑名单键
     */
    public static String requestBlacklist(String scope, String fingerprint) {
        return PREFIX + "risk:blacklist:" + scope + ":" + fingerprint;
    }
}
