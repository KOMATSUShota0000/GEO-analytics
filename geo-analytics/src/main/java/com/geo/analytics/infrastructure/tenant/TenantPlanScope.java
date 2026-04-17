package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Workspace-scoped string tenant id and subscription plan for quota / plan logic.
 * Uses {@link TenantContextHolder#CONTEXT} for organization UUID continuity.
 */
public final class TenantPlanScope {

    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<SubscriptionPlan> TENANT_PLAN = ScopedValue.newInstance();

    private TenantPlanScope() {}

    public static String getTenantId() {
        return TENANT_ID.isBound() ? TENANT_ID.get() : null;
    }

    public static SubscriptionPlan getTenantPlan() {
        return TENANT_PLAN.isBound() ? TENANT_PLAN.get() : null;
    }

    public static void executeWithTenant(UUID tenantId, Runnable runnable) {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .run(() -> {
                        SecurityContextHolder.setContext(prevSecurity);
                        runnable.run();
                    });
        } finally {
            restoreSecurityContext(prevSecurity);
        }
    }

    public static <T> T executeWithTenant(UUID tenantId, Callable<T> callable) {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .call(() -> wrapCallable(callable));
        } finally {
            restoreSecurityContext(prevSecurity);
        }
    }

    public static void executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Runnable runnable) {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .where(TENANT_PLAN, plan)
                    .run(() -> {
                        SecurityContextHolder.setContext(prevSecurity);
                        runnable.run();
                    });
        } finally {
            restoreSecurityContext(prevSecurity);
        }
    }

    public static <T> T executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Callable<T> callable) {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .where(TENANT_PLAN, plan)
                    .call(() -> wrapCallable(callable));
        } finally {
            restoreSecurityContext(prevSecurity);
        }
    }

    private static void restoreSecurityContext(SecurityContext previous) {
        if (previous == null || previous.getAuthentication() == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static <T> T wrapCallable(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
