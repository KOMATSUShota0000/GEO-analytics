package com.geo.analytics.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code math_debate_audit_events} 永続化直後の、WORM エクスポート用スナップショット（不変）。
 */
public record MathDebateAuditExportEvent(
        UUID id,
        UUID targetId,
        UUID workspaceId,
        String eventType,
        Map<String, Object> auditData,
        Instant createdAt) {}
