package com.sk.onlinemall.common.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionCallbackUtil {

    /**
     * 禁止实例化事务回调工具类。
     */
    private TransactionCallbackUtil() {
    }

    /**
     * 在当前事务成功提交后执行回调，无活动事务时立即执行。
     *
     * @param action 待执行回调
     */
    public static void afterCommit(Runnable action) {
        if (!hasActiveSynchronization()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 执行事务提交后的回调。
             */
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * 在当前事务未提交时执行回调，无活动事务时立即执行。
     *
     * @param action 待执行回调
     */
    public static void afterCompletionUnlessCommitted(Runnable action) {
        if (!hasActiveSynchronization()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 根据事务完成状态执行失败清理回调。
             *
             * @param status 事务完成状态
             */
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) action.run();
            }
        });
    }

    /**
     * 判断当前线程是否存在可注册回调的活动事务。
     *
     * @return 存在活动事务同步时返回 true
     */
    private static boolean hasActiveSynchronization() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }
}
