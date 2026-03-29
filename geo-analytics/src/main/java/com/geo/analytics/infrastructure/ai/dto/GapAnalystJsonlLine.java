package com.geo.analytics.infrastructure.ai.dto;

import java.util.UUID;

public record GapAnalystJsonlLine(UUID auditHistoryId, String fullPromptText) {}
