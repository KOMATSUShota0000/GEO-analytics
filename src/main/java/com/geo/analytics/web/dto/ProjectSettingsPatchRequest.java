package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProjectSettingsPatchRequest(
    @JsonProperty("auto_audit_enabled") Boolean autoAuditEnabled,
    @JsonProperty("notification_emails") List<String> notificationEmails) {
}
