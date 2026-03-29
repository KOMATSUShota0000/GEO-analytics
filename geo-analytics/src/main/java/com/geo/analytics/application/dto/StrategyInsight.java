package com.geo.analytics.application.dto;

import java.util.List;

public record StrategyInsight(String diagnosticMessage, List<String> recommendedActions, Double representativeModifiedZ) {}
