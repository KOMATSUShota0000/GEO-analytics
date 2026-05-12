package com.geo.analytics.application.dto;

import java.util.UUID;

public record CompetitorExtractionContext(UUID jobId, UUID projectId, String targetUrl) {}
