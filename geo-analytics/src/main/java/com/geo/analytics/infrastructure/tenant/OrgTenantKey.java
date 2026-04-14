package com.geo.analytics.infrastructure.tenant;

import java.util.UUID;

public record OrgTenantKey(UUID orgId, UUID tenantId) {}
