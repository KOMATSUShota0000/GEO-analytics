package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * オンボーディング・ディベート実況用 SSE ペイロード。送信前に {@code ObjectMapper} で JSON 化すること。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DebateOnboardingSseEvent(
        DebateOnboardingSseEventType eventType,
        DebateStreamPersona persona,
        DebateStreamPhase status,
        String message,
        DebatePartialScoresPayload partialScores,
        Instant timestamp,
        UUID sessionId) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DebatePartialScoresPayload(
            Integer round,
            List<Double> pSite,
            List<Double> agentMass,
            Double sDensity,
            Double qIntent,
            List<Double> currConfidences,
            List<Double> currCentroid,
            Double geoIg,
            Double wasserstein1) {}

    public static DebateOnboardingSseEvent error(String message, UUID sessionId) {
        return new DebateOnboardingSseEvent(
                DebateOnboardingSseEventType.ERROR,
                DebateStreamPersona.SYSTEM,
                null,
                message,
                null,
                Instant.now(),
                sessionId);
    }

    public enum DebateOnboardingSseEventType {
        NARRATION,
        SCORE_UPDATE,
        PHASE_CHANGE,
        DONE,
        ERROR
    }

    public enum DebateStreamPersona {
        ANALYST,
        INNOVATOR,
        SKEPTIC,
        DIRECTOR,
        SYSTEM
    }

    public enum DebateStreamPhase {
        GATHERING,
        ANALYZING,
        DEBATING,
        CONVERGING
    }
}
