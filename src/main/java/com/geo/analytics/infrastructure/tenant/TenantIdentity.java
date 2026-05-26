package com.geo.analytics.infrastructure.tenant;

import java.util.UUID;

public record TenantIdentity(UUID organizationId, UUID tenantId, UUID userId) {}
