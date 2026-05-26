package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitorShareEntry(String competitorName, Double share) {}
