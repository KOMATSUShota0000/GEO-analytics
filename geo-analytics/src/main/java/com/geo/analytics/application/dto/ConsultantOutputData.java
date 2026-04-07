package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultantOutputData(
        @JsonProperty("response") String response,
        @JsonProperty("extracted_brand_mention") String extractedBrandMention,
        @JsonProperty("token_count") Integer tokenCount,
        @JsonProperty("rank_position") Integer rankPosition,
        @JsonProperty("sentiment_intensity") Double sentimentIntensity,
        @JsonProperty("brand_mentioned") Boolean brandMentioned,
        @JsonProperty("prioritizedTasks") List<TaskDTO> prioritizedTasks,
        @JsonProperty("competitorComparison") List<CompetitorShareEntry> competitorComparison,
        @JsonProperty("reversalStrategy") String reversalStrategy) {
    public ConsultantOutputData {
        prioritizedTasks = prioritizedTasks == null ? List.of() : List.copyOf(prioritizedTasks);
        competitorComparison = competitorComparison == null ? null : List.copyOf(competitorComparison);
    }
    public SomScoreData toSomScoreData() {
        return new SomScoreData(tokenCount, rankPosition, sentimentIntensity, brandMentioned);
    }
}
