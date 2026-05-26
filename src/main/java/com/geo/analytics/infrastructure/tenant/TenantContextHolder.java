package com.geo.analytics.infrastructure.tenant;

import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;

public final class TenantContextHolder {

    public static final ScopedValue<TenantIdentity> CONTEXT = ScopedValue.newInstance();

    private TenantContextHolder() {}

    public static boolean isBound() {
        return CONTEXT.isBound();
    }

    public static TenantIdentity requireContext() {
        if (!CONTEXT.isBound()) {
            throw new IllegalStateException("Tenant identity is not bound in this scope");
        }
        return CONTEXT.get();
    }

    public static Optional<TenantIdentity> current() {
        return CONTEXT.isBound() ? Optional.of(CONTEXT.get()) : Optional.empty();
    }

    public static Optional<UUID> getOrganizationId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().organizationId();
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public static Optional<UUID> getTenantId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().tenantId();
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public static Optional<UUID> getUserId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().userId();
        return id == null ? Optional.empty() : Optional.of(id);
    }
}
