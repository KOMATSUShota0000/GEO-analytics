package com.geo.analytics.infrastructure.tenant;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.lang.ScopedValue;

public final class TenantContext {
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

    private TenantContext() {}

    public static String getTenantId() {
        return TENANT_ID.isBound() ? TENANT_ID.get() : null;
    }

    public static void executeWithTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TENANT_ID, tenantId.toString()).run(runnable);
    }

    public static <T> T executeWithTenant(UUID tenantId, Callable<T> callable) {
        try {
            return ScopedValue.where(TENANT_ID, tenantId.toString()).call(callable);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
