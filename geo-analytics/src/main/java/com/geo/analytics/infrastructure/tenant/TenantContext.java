package com.geo.analytics.infrastructure.tenant;

import com.geo.analytics.domain.enums.SubscriptionPlan;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.lang.ScopedValue;

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
        ScopedValue.where(TENANT_ID, tenantId.toString()).run(runnable);
    }

    public static <T> T executeWithTenant(UUID tenantId, Callable<T> callable) {
        return ScopedValue.where(TENANT_ID, tenantId.toString()).call(() -> wrapCallable(callable));
    }

    public static void executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Runnable runnable) {
        ScopedValue.where(TENANT_ID, tenantId.toString()).where(TENANT_PLAN, plan).run(runnable);
    }

    public static <T> T executeWithTenantAndPlan(UUID tenantId, SubscriptionPlan plan, Callable<T> callable) {
        return ScopedValue.where(TENANT_ID, tenantId.toString()).where(TENANT_PLAN, plan).call(() -> wrapCallable(callable));
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
