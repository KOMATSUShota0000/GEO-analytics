package com.geo.analytics.infrastructure.ai.dto;

import java.util.UUID;

public record BatchQueryLine(UUID queryId, String queryText) {}
