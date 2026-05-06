package com.geo.analytics.web.dto;

import com.geo.analytics.application.dto.OneClickCompetitorExtractionResult;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;

public record OneClickCompetitorExtractionResponse(
        IndustryType inferredIndustry,
        String inferredLocation,
        String inferenceEvidence,
        List<BenchmarkResponse> benchmarks,
        boolean usedFallback) {
    public static OneClickCompetitorExtractionResponse from(OneClickCompetitorExtractionResult result) {
        List<BenchmarkResponse> mapped =
                result.benchmarks().stream()
                        .map(
                                c ->
                                        new BenchmarkResponse(
                                                c.name(),
                                                c.websiteUrl(),
                                                c.rating(),
                                                c.reviewCount(),
                                                c.source().name(),
                                                c.selectionReason()))
                        .toList();
        return new OneClickCompetitorExtractionResponse(
                result.inferredIndustry(),
                result.inferredLocation(),
                result.inferenceEvidence(),
                mapped,
                result.usedFallback());
    }
}
