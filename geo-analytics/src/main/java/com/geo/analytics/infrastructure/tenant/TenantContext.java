package com.geo.analytics.infrastructure.tenant;

import java.util.UUID;

/**
 * Immutable tenant / organization binding carried by {@link TenantContextHolder#CONTEXT}.
 */
public record TenantContext(UUID organizationId, UUID tenantId, UUID userId) {}
