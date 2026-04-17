package com.geo.analytics.infrastructure.lock;

import com.geo.analytics.domain.lock.DistributedLockManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PostgreSQL アドバイザリロックを用いた {@link DistributedLockManager}。
 *
 * <p><b>CRITICAL（プール枯渇）:</b> ロック保持中に外部 API（AI エンジン、HTTP クライアント等）を呼び出さないこと。
 * ロックは JDBC 接続を占有するため、遅延の大きい I/O を挟むと同一プールを消費する他スレッドが飢餓し、サービス全体が停止する。
 *
 * <p>トランザクション内では {@code pg_try_advisory_xact_lock} を用い、コミット／ロールバックで PostgreSQL が自動解放する。
 * トランザクション外では {@code pg_try_advisory_lock} を指数バックオフで再試行し、{@code finally} でのみ手動解放する。
 */
@Component
@ConditionalOnProperty(name = "app.distributed-lock.impl", havingValue = "postgres", matchIfMissing = true)
public class PostgresDistributedLockManager implements DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresDistributedLockManager.class);

    private static final String TRY_SESSION_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String TRY_XACT_LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private static final long MAX_LOCK_WAIT_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final long INITIAL_BACKOFF_MS = 10L;
    private static final long MAX_BACKOFF_MS = 1_000L;

    private static final String TIMER_NAME = "distributed_lock.acquire";
    private static final String TAG_LOCK_KEY = "lockKey";

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;

    public PostgresDistributedLockManager(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public <T, X extends Throwable> T executeWithLock(String lockKey, Supplier<T> action) throws X {
        Objects.requireNonNull(lockKey, "lockKey");
        Objects.requireNonNull(action, "action");
        long id = AdvisoryLockKeyHasher.toAdvisoryLong(lockKey);
        Connection conn = DataSourceUtils.getConnection(dataSource);
        boolean sessionLockHeld = false;
        try {
            boolean transactional = TransactionSynchronizationManager.isActualTransactionActive();
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                if (transactional) {
                    boolean acquired;
                    try {
                        acquired = tryAdvisoryXactLock(conn, id);
                    } catch (SQLException e) {
                        throw new LockAcquisitionException("pg_try_advisory_xact_lock failed for key=" + lockKey, e);
                    }
                    if (!acquired) {
                        throw new LockAcquisitionException(
                                "pg_try_advisory_xact_lock returned false for key=" + lockKey
                                        + " (contention in same transaction scope)");
                    }
                } else {
                    acquireSessionLockWithBackoff(conn, id, lockKey);
                    sessionLockHeld = true;
                }
            } finally {
                sample.stop(buildAcquireTimer(lockKey));
            }
            return action.get();
        } finally {
            if (sessionLockHeld) {
                releaseQuietly(conn, id);
            }
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    @Override
    public void executeWithLock(String lockKey, Runnable action) {
        Objects.requireNonNull(lockKey, "lockKey");
        Objects.requireNonNull(action, "action");
        long id = AdvisoryLockKeyHasher.toAdvisoryLong(lockKey);
        Connection conn = DataSourceUtils.getConnection(dataSource);
        boolean sessionLockHeld = false;
        try {
            boolean transactional = TransactionSynchronizationManager.isActualTransactionActive();
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                if (transactional) {
                    boolean acquired;
                    try {
                        acquired = tryAdvisoryXactLock(conn, id);
                    } catch (SQLException e) {
                        throw new LockAcquisitionException("pg_try_advisory_xact_lock failed for key=" + lockKey, e);
                    }
                    if (!acquired) {
                        throw new LockAcquisitionException(
                                "pg_try_advisory_xact_lock returned false for key=" + lockKey
                                        + " (contention in same transaction scope)");
                    }
                } else {
                    acquireSessionLockWithBackoff(conn, id, lockKey);
                    sessionLockHeld = true;
                }
            } finally {
                sample.stop(buildAcquireTimer(lockKey));
            }
            action.run();
        } finally {
            if (sessionLockHeld) {
                releaseQuietly(conn, id);
            }
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private Timer buildAcquireTimer(String lockKey) {
        return Timer.builder(TIMER_NAME).tag(TAG_LOCK_KEY, lockKey).register(meterRegistry);
    }

    private static boolean tryAdvisoryXactLock(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TRY_XACT_LOCK_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean trySessionLock(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TRY_SESSION_LOCK_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void acquireSessionLockWithBackoff(Connection conn, long id, String lockKey) {
        long deadlineNanos = System.nanoTime() + MAX_LOCK_WAIT_NANOS;
        long backoffMs = INITIAL_BACKOFF_MS;
        while (true) {
            try {
                if (trySessionLock(conn, id)) {
                    return;
                }
            } catch (SQLException e) {
                throw new LockAcquisitionException("pg_try_advisory_lock failed for key=" + lockKey, e);
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new LockAcquisitionException(
                        "Advisory session lock not acquired within "
                                + TimeUnit.NANOSECONDS.toSeconds(MAX_LOCK_WAIT_NANOS)
                                + "s for key="
                                + lockKey);
            }
            long sleepMs = Math.min(backoffMs, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
            sleepMs = Math.max(sleepMs, 1L);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockAcquisitionException("Interrupted waiting for advisory lock: " + lockKey, e);
            }
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }

    private static void releaseQuietly(Connection conn, long id) {
        try (PreparedStatement ps = conn.prepareStatement(UNLOCK_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !rs.getBoolean(1)) {
                    log.warn("pg_advisory_unlock returned false (not held by this session?) id={}", id);
                }
            }
        } catch (SQLException e) {
            log.warn("pg_advisory_unlock failed id={}", id, e);
        }
    }

    /**
     * アドバイザリロックが規定時間内に取得できなかった場合にスローする。
     */
    public static final class LockAcquisitionException extends RuntimeException {
        public LockAcquisitionException(String message) {
            super(message);
        }

        public LockAcquisitionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
