package com.sk.onlinemall.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CommonUtilityTests {

    /**
     * 验证 SHA-256 摘要输出稳定的十六进制文本。
     */
    @Test
    void shouldCreateSha256HexDigest() {
        assertThat(DigestUtil.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    /**
     * 验证文本规范化和截断使用统一规则。
     */
    @Test
    void shouldNormalizeAndTruncateText() {
        assertThat(TextUtil.normalizeLowercase("  AbC  ")).isEqualTo("abc");
        assertThat(TextUtil.normalizeUppercaseToNull("  active ")).isEqualTo("ACTIVE");
        assertThat(TextUtil.trimToNull("   ")).isNull();
        assertThat(TextUtil.truncate("abcdef", 4)).isEqualTo("abcd");
    }

    /**
     * 验证紧凑 UUID 不包含连字符且长度固定。
     */
    @Test
    void shouldCreateCompactUuid() {
        assertThat(IdentifierUtil.compactUuid()).matches("[0-9a-f]{32}");
    }

    /**
     * 验证事务提交回调只在提交阶段执行。
     */
    @Test
    void shouldRunCallbackAfterTransactionCommit() {
        AtomicBoolean executed = new AtomicBoolean();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TransactionCallbackUtil.afterCommit(() -> executed.set(true));
            assertThat(executed).isFalse();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(executed).isTrue();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    /**
     * 验证无活动事务时提交回调立即执行。
     */
    @Test
    void shouldRunCommitCallbackImmediatelyWithoutTransaction() {
        AtomicBoolean executed = new AtomicBoolean();

        TransactionCallbackUtil.afterCommit(() -> executed.set(true));

        assertThat(executed).isTrue();
    }
}
