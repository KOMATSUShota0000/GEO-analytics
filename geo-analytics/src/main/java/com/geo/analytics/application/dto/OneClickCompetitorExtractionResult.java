package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;

public record OneClickCompetitorExtractionResult(
        IndustryType inferredIndustry,
        String inferredLocation,
        String inferenceEvidence,
        List<BenchmarkCandidate> benchmarks,
        boolean usedFallback) {
}
