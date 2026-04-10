package com.geo.analytics.infrastructure.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContextHolder {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {}

    private record Context(UUID organizationId, UUID tenantId) {}

    public static void set(UUID organizationId, UUID tenantId) {
        HOLDER.set(new Context(organizationId, tenantId));
    }

    public static Optional<UUID> getOrganizationId() {
        Context c = HOLDER.get();
        if (c == null || c.organizationId == null) {
            return Optional.empty();
        }
        return Optional.of(c.organizationId);
    }

    public static Optional<UUID> getTenantId() {
        Context c = HOLDER.get();
        if (c == null || c.tenantId == null) {
            return Optional.empty();
        }
        return Optional.of(c.tenantId);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
