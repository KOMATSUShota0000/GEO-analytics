package com.geo.analytics.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SerpApiResponse(
    @JsonProperty("organic_results") JsonNode organicResults,
    @JsonProperty("related_questions") JsonNode relatedQuestions,
    @JsonProperty("answer_box") JsonNode answerBox,
    @JsonProperty("ai_overview") JsonNode aiOverview
) {}
