package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;

public record RubricItemAudit(
        @JsonProperty("criterionId") RubricCriterionId criterionId,
        @JsonProperty("status") RubricVerdictStatus status,
        @JsonProperty("evidence") String evidence) {
}
