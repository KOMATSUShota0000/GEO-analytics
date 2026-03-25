package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SomScoreData(
    String response,
    Boolean brandMentioned,
    Integer mentionRank,
    Double confidenceScore,
    Integer overallScore,
    List<TaskDTO> prioritizedTasks,
    List<CompetitorShareEntry> competitorComparison,
    String reversalStrategy
) {
    public SomScoreData {
        prioritizedTasks = prioritizedTasks == null ? List.of() : List.copyOf(prioritizedTasks);
        competitorComparison = competitorComparison == null ? null : List.copyOf(competitorComparison);
    }
}
