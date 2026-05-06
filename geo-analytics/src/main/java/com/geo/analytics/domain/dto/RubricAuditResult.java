package com.geo.analytics.domain.dto;

import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;

public record RubricAuditResult(
        RubricCriterionId criterionId,
        RubricVerdictStatus verdict,
        String evidence,
        double score) {
    public RubricAuditResult {
        if (criterionId == null) {
            throw new IllegalArgumentException("criterionId");
        }
        if (verdict == null) {
            throw new IllegalArgumentException("verdict");
        }
        evidence = evidence == null ? "" : evidence;
    }
}
