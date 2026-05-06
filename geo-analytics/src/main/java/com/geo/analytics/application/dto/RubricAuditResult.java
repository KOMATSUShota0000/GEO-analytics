package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RubricAuditResult(@JsonProperty("items") List<RubricItemAudit> items) {
    public RubricAuditResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
