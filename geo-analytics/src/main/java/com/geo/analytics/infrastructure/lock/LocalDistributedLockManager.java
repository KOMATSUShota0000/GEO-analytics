package com.geo.analytics.infrastructure.lock;

import com.geo.analytics.domain.lock.DistributedLockManager;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 同一 JVM 内のみ有効な {@link DistributedLockManager}（ユニットテスト・ローカル用途）。
 * PostgreSQL のアドバイザリロック API に依存しない。
 */
@Component
@ConditionalOnProperty(name = "app.distributed-lock.impl", havingValue = "local")
public class LocalDistributedLockManager implements DistributedLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T, X extends Throwable> T executeWithLock(String lockKey, Supplier<T> action) throws X {
        Objects.requireNonNull(lockKey, "lockKey");
        Objects.requireNonNull(action, "action");
        ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void executeWithLock(String lockKey, Runnable action) {
        Objects.requireNonNull(lockKey, "lockKey");
        Objects.requireNonNull(action, "action");
        ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
