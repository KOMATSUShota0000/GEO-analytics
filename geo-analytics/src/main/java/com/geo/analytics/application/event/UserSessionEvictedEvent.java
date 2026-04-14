package com.geo.analytics.application.event;

import java.util.UUID;

public record UserSessionEvictedEvent(UUID userId, UUID organizationId) {}
