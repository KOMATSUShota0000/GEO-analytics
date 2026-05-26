package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectSettingsPatchRequest(
    @JsonProperty("auto_audit_enabled") Boolean autoAuditEnabled,
    @JsonProperty("slack_webhook_url") String slackWebhookUrl,
    @JsonProperty("notification_email") String notificationEmail) {
}
