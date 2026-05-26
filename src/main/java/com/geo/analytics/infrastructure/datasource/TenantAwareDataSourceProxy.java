package com.geo.analytics.infrastructure.datasource;

import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * {@link HikariDataSource} を {@link DelegatingDataSource} で包み、取得した {@link Connection} をプロキシで包む。
 *
 * <p>プール返却（{@link Connection#close()}）時に、PostgreSQL かつ未完了トランザクション（{@code autoCommit == false}）を検知した場合は
 * {@link HikariDataSource#evictConnection(java.sql.Connection)} でプールから追放し、直後に必ず {@code delegate.close()} を呼び出して
 * HikariCP のライフサイクルを完了させる（接続リーク防止）。
 *
 * <p>{@link Connection#unwrap(Class)} / {@link Connection#isWrapperFor(Class)} はドライバ固有型へのバイパスを拒否する。
 */
public class TenantAwareDataSourceProxy extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSourceProxy.class);

    private static final String EVICT_COUNTER = "tenant_connection_evict_total";
    private static final String REASON_TAG = "reason";
    private static final String REASON_OPEN_TRANSACTION = "open_transaction";

    private final HikariDataSource hikariPool;
    private final MeterRegistry meterRegistry;

    public TenantAwareDataSourceProxy(HikariDataSource hikariPool, MeterRegistry meterRegistry) {
        this.hikariPool = Objects.requireNonNull(hikariPool, "hikariPool");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        setTargetDataSource(hikariPool);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(super.getConnection(username, password));
    }

    private Connection wrapConnection(Connection delegate) {
        ClassLoader cl = TenantAwareDataSourceProxy.class.getClassLoader();
        if (cl == null) {
            cl = Connection.class.getClassLoader();
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection)
                Proxy.newProxyInstance(
                        cl,
                        new Class<?>[] {Connection.class, TenantAwareProxiedConnection.class},
                        new GuardedConnectionHandler(this, delegate, closed));
    }

    /**
     * PostgreSQL かつ未コミット TX の接続をプールから追放する。{@code evictConnection(delegate)} の直後に必ず {@code delegate.close()} を
     * 呼ぶこと（本ハンドラの {@code close} 経路で実施）。
     */
    void evictIfOpenTransactionBeforeClose(Connection delegate) {
        try {
            if (delegate.isClosed()) {
                return;
            }
            if (!isPostgreSql(delegate)) {
                return;
            }
            if (!delegate.getAutoCommit()) {
                log.warn(
                        "Evicting Hikari connection: open transaction at pool return (autoCommit=false). "
                                + "isolationContext={}",
                        describeIsolationContext());
                meterRegistry.counter(EVICT_COUNTER, REASON_TAG, REASON_OPEN_TRANSACTION).increment();
                hikariPool.evictConnection(delegate);
            }
        } catch (SQLException e) {
            log.warn(
                    "Pre-close connection state check failed; proceeding with delegate.close() isolationContext={}",
                    describeIsolationContext(),
                    e);
        }
    }

    private static String describeIsolationContext() {
        StringBuilder sb = new StringBuilder(256);
        TenantContextHolder.current()
                .ifPresentOrElse(
                        ctx -> sb.append("TenantContextHolder{orgId=")
                                .append(ctx.organizationId())
                                .append(",workspaceId=")
                                .append(ctx.tenantId())
                                .append(",userId=")
                                .append(ctx.userId())
                                .append('}'),
                        () -> sb.append("TenantContextHolder=unbound"));
        sb.append("; TenantPlanScope.tenantId=");
        TenantPlanScope.currentTenantIdString().ifPresentOrElse(sb::append, () -> sb.append("unbound"));
        sb.append("; TenantPlanScope.subscriptionPlan=");
        TenantPlanScope.currentSubscriptionPlan()
                .map(Enum::name)
                .ifPresentOrElse(sb::append, () -> sb.append("unbound"));
        return sb.toString();
    }

    private static boolean isPostgreSql(Connection connection) {
        try {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException e) {
            return false;
        }
    }

    private static final class GuardedConnectionHandler implements InvocationHandler {

        private final TenantAwareDataSourceProxy owner;
        private final Connection delegate;
        private final AtomicBoolean closed;

        private GuardedConnectionHandler(TenantAwareDataSourceProxy owner, Connection delegate, AtomicBoolean closed) {
            this.owner = owner;
            this.delegate = delegate;
            this.closed = closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            int argc = args == null ? 0 : args.length;

            if ("unwrap".equals(name) && argc == 1 && args[0] instanceof Class<?> iface) {
                return unwrapStrict(proxy, iface);
            }
            if ("isWrapperFor".equals(name) && argc == 1 && args[0] instanceof Class<?> iface) {
                return isWrapperForStrict(proxy, iface);
            }

            if ("close".equals(name) && argc == 0) {
                return invokeClose(method, args);
            }

            return invokeDelegate(method, args);
        }

        private Object invokeClose(Method method, Object[] args) throws Throwable {
            if (!closed.compareAndSet(false, true)) {
                return invokeDelegate(method, args);
            }
            owner.evictIfOpenTransactionBeforeClose(delegate);
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw cause;
            }
        }

        private Object invokeDelegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    throw cause;
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unwrapStrict(Object proxy, Class<T> iface) throws SQLException {
        Objects.requireNonNull(iface, "iface");
        if (iface.isInstance(proxy)) {
            return iface.cast(proxy);
        }
        throw new SQLException(
                "Unwrap to "
                        + iface.getName()
                        + " is denied by tenant connection guard (driver-specific bypass blocked). "
                        + "Allowed targets are types implemented by this proxy (java.sql.Connection and "
                        + TenantAwareProxiedConnection.class.getName()
                        + ").");
    }

    private static boolean isWrapperForStrict(Object proxy, Class<?> iface) {
        return iface != null && iface.isInstance(proxy);
    }
}
