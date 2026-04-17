package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
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

    /** Hibernate / string key: absent when {@link #TENANT_ID} is not bound in this scope. */
    public static Optional<String> currentTenantIdString() {
        return TENANT_ID.isBound() ? Optional.of(TENANT_ID.get()) : Optional.empty();
    }

    public static Optional<SubscriptionPlan> currentSubscriptionPlan() {
        return TENANT_PLAN.isBound() ? Optional.of(TENANT_PLAN.get()) : Optional.empty();
    }

    public static String requireTenantIdString() {
        if (!TENANT_ID.isBound()) {
            throw new IllegalStateException("Workspace tenant id is not bound in this scope");
        }
        return TENANT_ID.get();
    }

    public static SubscriptionPlan requireSubscriptionPlan() {
        if (!TENANT_PLAN.isBound()) {
            throw new IllegalStateException("Subscription plan is not bound in this scope");
        }
        return TENANT_PLAN.get();
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

    public static <T, X extends Throwable> T executeWithTenant(UUID tenantId, Supplier<T> supplier) throws X {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .call(() -> {
                        SecurityContextHolder.setContext(prevSecurity);
                        return supplier.get();
                    });
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

    public static <T, X extends Throwable> T executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Supplier<T> supplier)
            throws X {
        Optional<TenantContext> cur = TenantContextHolder.current();
        UUID org = cur.map(TenantContext::organizationId).orElse(null);
        UUID uid = cur.map(TenantContext::userId).orElse(null);
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        try {
            return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantContext(org, tenantId, uid))
                    .where(TENANT_ID, tenantId.toString())
                    .where(TENANT_PLAN, plan)
                    .call(() -> {
                        SecurityContextHolder.setContext(prevSecurity);
                        return supplier.get();
                    });
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
}
