package com.geo.analytics.domain.event;

import java.util.UUID;

public record ProjectAuditCompletedEvent(UUID projectId, UUID jobId, UUID workspaceId) {
}
