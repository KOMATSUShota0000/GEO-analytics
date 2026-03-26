package com.geo.analytics.infrastructure.tenant;
import java.util.UUID;
import java.util.concurrent.Callable;
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private TenantContext() {
    }
    public static void setCurrentTenant(String tenantId) {
        CURRENT.set(tenantId);
    }
    public static String getTenantId() {
        return CURRENT.get();
    }
    public static void clear() {
        CURRENT.remove();
    }
    public static void executeWithTenant(UUID tenantId, Runnable runnable) {
        String previous = CURRENT.get();
        try {
            CURRENT.set(tenantId.toString());
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }
    public static <T> T executeWithTenant(UUID tenantId, Callable<T> callable) {
        String previous = CURRENT.get();
        try {
            CURRENT.set(tenantId.toString());
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }
}
