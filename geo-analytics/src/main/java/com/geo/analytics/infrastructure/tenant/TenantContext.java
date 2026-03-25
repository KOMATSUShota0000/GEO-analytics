package com.geo.analytics.infrastructure.tenant;
import java.util.UUID;
import java.util.concurrent.Callable;
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private TenantContext() {
    }
    public static UUID get() {
        return CURRENT.get();
    }
    public static void clear() {
        CURRENT.remove();
    }
    public static void executeWithTenant(UUID tenantId, Runnable runnable) {
        UUID previous = CURRENT.get();
        try {
            CURRENT.set(tenantId);
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
        UUID previous = CURRENT.get();
        try {
            CURRENT.set(tenantId);
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
