package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.domain.enums.SubscriptionPlan;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.lang.ScopedValue;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TenantContext {
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<SubscriptionPlan> TENANT_PLAN = ScopedValue.newInstance();

    private TenantContext() {}

    public static String getTenantId() {
        return TENANT_ID.isBound() ? TENANT_ID.get() : null;
    }

    public static SubscriptionPlan getTenantPlan() {
        return TENANT_PLAN.isBound() ? TENANT_PLAN.get() : null;
    }

    public static void executeWithTenant(UUID tenantId, Runnable runnable) {
        Optional<UUID> prevOrg = TenantContextHolder.getOrganizationId();
        Optional<UUID> prevTenant = TenantContextHolder.getTenantId();
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        TenantContextHolder.set(prevOrg.orElse(null), tenantId);
        try {
            SecurityContextHolder.setContext(prevSecurity);
            ScopedValue.where(TENANT_ID, tenantId.toString()).run(runnable);
        } finally {
            restoreHolder(prevOrg, prevTenant);
            restoreSecurityContext(prevSecurity);
        }
    }

    public static <T> T executeWithTenant(UUID tenantId, Callable<T> callable) {
        Optional<UUID> prevOrg = TenantContextHolder.getOrganizationId();
        Optional<UUID> prevTenant = TenantContextHolder.getTenantId();
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        TenantContextHolder.set(prevOrg.orElse(null), tenantId);
        try {
            SecurityContextHolder.setContext(prevSecurity);
            return ScopedValue.where(TENANT_ID, tenantId.toString()).call(() -> wrapCallable(callable));
        } finally {
            restoreHolder(prevOrg, prevTenant);
            restoreSecurityContext(prevSecurity);
        }
    }

    public static void executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Runnable runnable) {
        Optional<UUID> prevOrg = TenantContextHolder.getOrganizationId();
        Optional<UUID> prevTenant = TenantContextHolder.getTenantId();
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        TenantContextHolder.set(prevOrg.orElse(null), tenantId);
        try {
            SecurityContextHolder.setContext(prevSecurity);
            ScopedValue.where(TENANT_ID, tenantId.toString()).where(TENANT_PLAN, plan).run(runnable);
        } finally {
            restoreHolder(prevOrg, prevTenant);
            restoreSecurityContext(prevSecurity);
        }
    }

    public static <T> T executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Callable<T> callable) {
        Optional<UUID> prevOrg = TenantContextHolder.getOrganizationId();
        Optional<UUID> prevTenant = TenantContextHolder.getTenantId();
        SecurityContext prevSecurity = SecurityContextHolder.getContext();
        TenantContextHolder.set(prevOrg.orElse(null), tenantId);
        try {
            SecurityContextHolder.setContext(prevSecurity);
            return ScopedValue.where(TENANT_ID, tenantId.toString()).where(TENANT_PLAN, plan).call(() -> wrapCallable(callable));
        } finally {
            restoreHolder(prevOrg, prevTenant);
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

    private static void restoreHolder(Optional<UUID> prevOrg, Optional<UUID> prevTenant) {
        if (prevOrg.isEmpty() && prevTenant.isEmpty()) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.set(prevOrg.orElse(null), prevTenant.orElse(null));
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
