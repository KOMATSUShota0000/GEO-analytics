package com.geo.analytics.infrastructure.persistence;

import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * トランザクション内で RLS 用セッション変数を設定する。{@link Ordered#LOWEST_PRECEDENCE} で
 * {@link org.springframework.transaction.interceptor.TransactionInterceptor} と同じ最終優先度バケットに置き、
 * 通常はインフラ側アドバイザの登録順によりトランザクション開始後に本アドバイスが評価されることを期待する。
 *
 * <p>{@link GlobalAccess} かつアクティブなトランザクションがある場合は {@code app.rls_bypass} を
 * {@code 'on'} にし、マイグレーションで定義されたテーブル（例: {@code user_sessions}）の RLS を
 * データベース側ポリシーで明示的に緩和する。
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "app.security.rls", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
public class RlsConnectionInterceptor implements Ordered {

    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String COUNTER_GLOBAL_ACCESS = "rls_global_access_total";
    private static final String COUNTER_NAKED_BLOCKED = "rls_naked_query_blocked_total";
    private static final String TAG_CLASS = "class";
    private static final String TAG_METHOD = "method";

    private static final String SET_ORG = "SELECT set_config('app.current_org_id', ?, true)";
    private static final String SET_TENANT = "SELECT set_config('app.current_tenant_id', ?, true)";
    private static final String SET_RLS_BYPASS = "SELECT set_config('app.rls_bypass', 'on', true)";
    private static final String RESOLVE_ORG = "SELECT organization_id FROM workspaces WHERE id = ? AND deleted_at IS NULL";

    private final EntityManager entityManager;
    private final JdbcTemplate batchJdbcTemplate;
    private final boolean rlsEnabled;
    private final MeterRegistry meterRegistry;

    public RlsConnectionInterceptor(
            EntityManager entityManager,
            @Qualifier("batchJdbcTemplate") JdbcTemplate batchJdbcTemplate,
            AppProperties appProperties,
            MeterRegistry meterRegistry) {
        this.entityManager = entityManager;
        this.batchJdbcTemplate = batchJdbcTemplate;
        this.meterRegistry = meterRegistry;
        boolean enabled = true;
        if (appProperties.getSecurity() != null && appProperties.getSecurity().getRls() != null) {
            enabled = appProperties.getSecurity().getRls().isEnabled();
        }
        this.rlsEnabled = enabled;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Around(
            "@within(org.springframework.transaction.annotation.Transactional) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object aroundTransactional(ProceedingJoinPoint pjp) throws Throwable {
        if (!rlsEnabled) {
            return pjp.proceed();
        }

        boolean global = hasGlobalAccess(pjp);
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        if (!global && !txActive) {
            recordNakedQueryBlocked(pjp);
            throw new SecurityException(
                    "Database access without an active transaction and without @GlobalAccess is blocked "
                            + "(possible missing @Transactional).");
        }

        if (global) {
            recordGlobalAccess(pjp);
        }

        if (!txActive) {
            return pjp.proceed();
        }

        Optional<UUID> org = TenantContextHolder.getOrganizationId();
        Optional<UUID> tenant = TenantContextHolder.getTenantId();
        if (org.isEmpty() && tenant.isEmpty()) {
            if (global) {
                enableRlsBypassOnConnection();
                return pjp.proceed();
            }
            throw new SecurityException(
                    "Transactional database access requires tenant context (organization or workspace). "
                            + "Annotate with @GlobalAccess only for intentional system routes.");
        }
        UUID resolvedOrg = org.orElseGet(() -> resolveOrgFromWorkspace(tenant.orElse(null)));
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            if (global) {
                applyRlsBypass(connection);
            }
            if (resolvedOrg != null) {
                try (PreparedStatement ps = connection.prepareStatement(SET_ORG)) {
                    ps.setString(1, resolvedOrg.toString());
                    ps.execute();
                }
            }
            if (tenant.isPresent()) {
                try (PreparedStatement ps = connection.prepareStatement(SET_TENANT)) {
                    ps.setString(1, tenant.get().toString());
                    ps.execute();
                }
            }
        });
        return pjp.proceed();
    }

    private void enableRlsBypassOnConnection() {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(RlsConnectionInterceptor::applyRlsBypass);
    }

    private static void applyRlsBypass(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(SET_RLS_BYPASS);
        }
    }

    private static boolean hasGlobalAccess(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        if (AnnotatedElementUtils.hasAnnotation(method, GlobalAccess.class)) {
            return true;
        }
        Class<?> targetClass = pjp.getTarget() != null ? pjp.getTarget().getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.hasAnnotation(targetClass, GlobalAccess.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), GlobalAccess.class);
    }

    private void recordGlobalAccess(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        String className = m.getDeclaringClass().getName();
        String methodName = m.getName();
        SECURITY_AUDIT.info(
                "RlsConnectionInterceptor: @GlobalAccess invocation allowed class={} method={} target={}",
                className,
                methodName,
                pjp.getTarget() != null ? pjp.getTarget().getClass().getName() : "null");
        meterRegistry.counter(COUNTER_GLOBAL_ACCESS, TAG_CLASS, className, TAG_METHOD, methodName).increment();
    }

    private void recordNakedQueryBlocked(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        String className = m.getDeclaringClass().getName();
        String methodName = m.getName();
        SECURITY_AUDIT.error(
                "RlsConnectionInterceptor: blocked naked (non-transactional) database access class={} method={} target={}",
                className,
                methodName,
                pjp.getTarget() != null ? pjp.getTarget().getClass().getName() : "null");
        meterRegistry.counter(COUNTER_NAKED_BLOCKED, TAG_CLASS, className, TAG_METHOD, methodName).increment();
    }

    private UUID resolveOrgFromWorkspace(UUID workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        try {
            return batchJdbcTemplate.queryForObject(RESOLVE_ORG, UUID.class, workspaceId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
