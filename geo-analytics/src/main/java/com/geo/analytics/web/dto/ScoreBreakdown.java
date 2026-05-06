package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ScoreBreakdown(
        @JsonProperty("ai_audit_total") double aiAuditTotal,
        @JsonProperty("meo_total") double meoTotal,
        @JsonProperty("machine_readability_total") double machineReadabilityTotal,
        @JsonProperty("final_score") double finalScore) {

    public static final double MAX_AI_AUDIT = 50.0d;
    public static final double MAX_MEO = 25.0d;
    public static final double MAX_MACHINE_READABILITY = 25.0d;

    public static ScoreBreakdown empty() {
        return new ScoreBreakdown(0.0d, 0.0d, 0.0d, 0.0d);
    }
}
