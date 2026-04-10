package com.geo.analytics.infrastructure.persistence;

import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RlsConnectionInterceptor {

    private static final String SET_ORG = "SELECT set_config('app.current_org_id', ?, true)";
    private static final String SET_TENANT = "SELECT set_config('app.current_tenant_id', ?, true)";

    private final EntityManager entityManager;
    private final boolean rlsEnabled;

    public RlsConnectionInterceptor(EntityManager entityManager, AppProperties appProperties) {
        this.entityManager = entityManager;
        boolean enabled = true;
        if (appProperties.getSecurity() != null && appProperties.getSecurity().getRls() != null) {
            enabled = appProperties.getSecurity().getRls().isEnabled();
        }
        this.rlsEnabled = enabled;
    }

    @Around(
            "@within(org.springframework.transaction.annotation.Transactional) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object aroundTransactional(ProceedingJoinPoint pjp) throws Throwable {
        if (!rlsEnabled || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return pjp.proceed();
        }
        Optional<UUID> org = TenantContextHolder.getOrganizationId();
        Optional<UUID> tenant = TenantContextHolder.getTenantId();
        if (org.isEmpty() && tenant.isEmpty()) {
            return pjp.proceed();
        }
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            if (org.isPresent()) {
                try (PreparedStatement ps = connection.prepareStatement(SET_ORG)) {
                    ps.setString(1, org.get().toString());
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
}
