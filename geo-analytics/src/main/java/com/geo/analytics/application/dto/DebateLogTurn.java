package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * マルチエージェント議論ログの1発言。ペルソナ別の注視レンズと発言本文。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebateLogTurn(
        @JsonProperty("persona") String persona,
        @JsonProperty("focus_lens") String focusLens,
        @JsonProperty("statement") String statement) {}
