package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 議論の合意から導かれたロードマップ上の1アクション。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoadmapConsensusItem(
        @JsonProperty("phase_order") Integer phaseOrder,
        @JsonProperty("title") String title,
        @JsonProperty("rationale") String rationale,
        @JsonProperty("expected_impact_roi_hint") String expectedImpactRoiHint) {}
