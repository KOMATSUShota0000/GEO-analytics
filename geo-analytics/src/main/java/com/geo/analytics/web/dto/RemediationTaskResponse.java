package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.model.RemediationTask;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RemediationTaskResponse(
        UUID id,
        String category,
        String priority,
        String title,
        String content,
        @JsonProperty("impact_score") double impactScore) {

    public static RemediationTaskResponse from(RemediationTask task) {
        if (task == null) {
            return null;
        }
        return new RemediationTaskResponse(
                task.id(),
                task.category().name(),
                task.priority().name(),
                task.title(),
                task.content(),
                task.impactScore());
    }
}
