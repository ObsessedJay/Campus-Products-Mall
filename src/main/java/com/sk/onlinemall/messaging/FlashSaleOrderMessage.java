package com.sk.onlinemall.messaging;

public record FlashSaleOrderMessage(
        String requestNo,
        Long activityId,
        Long productId,
        Long userId,
        String username,
        Integer quantity,
        int attempt,
        Long skuId) {

    /**
     * 创建不指定规格的兼容抢购消息。
     *
     * @param requestNo 业务请求号
     * @param activityId 活动主键
     * @param productId 商品主键
     * @param userId 用户主键
     * @param username 用户名
     * @param quantity 购买数量
     * @param attempt 消费尝试次数
     */
    public FlashSaleOrderMessage(String requestNo, Long activityId, Long productId, Long userId,
                                 String username, Integer quantity, int attempt) {
        this(requestNo, activityId, productId, userId, username, quantity, attempt, null);
    }

    /**
     * 生成增加一次重试计数的消息。
     *
     * @return 转换后的结果
     */
    public FlashSaleOrderMessage nextAttempt() {
        return new FlashSaleOrderMessage(requestNo, activityId, productId, userId, username, quantity,
                attempt + 1, skuId);
    }
}
