package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectSettingsResponse(
    @JsonProperty("project_id") UUID projectId,
    @JsonProperty("auto_audit_enabled") boolean autoAuditEnabled,
    @JsonProperty("slack_webhook_url") String slackWebhookUrl,
    @JsonProperty("notification_email") String notificationEmail,
    @JsonProperty("last_audit_at") LocalDateTime lastAuditAt) {
}
