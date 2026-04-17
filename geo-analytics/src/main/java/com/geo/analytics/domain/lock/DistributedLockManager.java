package com.geo.analytics.domain.lock;

import java.util.function.Supplier;

/**
 * DB ベンダー非依存の分散ロック抽象。ロックの取得・解放はメソッド呼び出しのスコープ内に閉じ、
 * 呼び出し側が {@code lock()} / {@code unlock()} を直接扱わない（ScopedValue と同様の「構造化」）ことで解放漏れを防ぐ。
 *
 * <p>マルチテナントでは、キーにテナント境界を含めることを推奨する（例:
 * {@code tenantUuid + ":" + resourceKind + ":" + resourceId}）。{@link com.geo.analytics.infrastructure.tenant.TenantContextHolder}
 * からテナント ID を取得してキーを組み立てるのは呼び出し側（アプリケーションサービス）の責務とする。
 */
public interface DistributedLockManager {

    /**
     * {@code lockKey} 用の排他を取得し、{@code action} の実行中だけ保持する。{@code action} 完了後（正常・例外のいずれでも）に解放する。
     *
     * @param lockKey 論理キー（テナント境界を含めることを推奨）
     * @param action ロック保持中に実行する処理（検査例外はそのまま伝播してよい）
     */
    <T, X extends Throwable> T executeWithLock(String lockKey, Supplier<T> action) throws X;

    /**
     * 戻り値のない版。{@link #executeWithLock(String, Supplier)} と同様にスコープ終了時に必ず解放する。
     */
    void executeWithLock(String lockKey, Runnable action);
}
