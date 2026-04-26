package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.matching.TokenizerManager;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class InformationTheoryBasedAggregatorTest {

    private final InformationTheoryBasedAggregator aggregator =
            new InformationTheoryBasedAggregator(mock(TokenizerManager.class));

    @Test
    void aggregationVersion_isV11GeoPure() {
        assertThat(InformationTheoryBasedAggregator.AGGREGATION_CALCULATION_VERSION).isEqualTo("V11_GEO_PURE");
    }

    @Test
    void averageAiCitationPosition_allNull_yieldsNull() {
        var insights = new LinkedHashMap<ModelType, String>();
        insights.put(ModelType.GEMINI, "{}");
        var a = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                10.0,
                true,
                1,
                50,
                10,
                null,
                1.0,
                "B",
                5,
                0.0,
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                List.of(),
                insights,
                10.0);
        var b = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                10.0,
                true,
                1,
                50,
                10,
                null,
                1.0,
                "B",
                5,
                0.0,
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                List.of(),
                insights,
                10.0);
        var request = new VerificationRequest("B", "q", "https://example.com/", null, null);
        var out = aggregator.aggregate(List.of(a, b), request);
        assertThat(out.calculationVersion()).isEqualTo("V11_GEO_PURE");
        assertThat(out.aiCitationPosition()).isNull();
    }

    @Test
    void averageAiCitationPosition_skipsNulls_usesHalfUpRounding() {
        var insights = new LinkedHashMap<ModelType, String>();
        insights.put(ModelType.GEMINI, "{}");
        var r1 = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                10.0,
                true,
                1,
                50,
                10,
                1,
                1.0,
                "B",
                5,
                0.0,
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                List.of(),
                insights,
                10.0);
        var rNull = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                10.0,
                true,
                1,
                50,
                10,
                null,
                1.0,
                "B",
                5,
                0.0,
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                List.of(),
                insights,
                10.0);
        var r3 = new VerificationResponse(
                ModelType.GEMINI,
                "{}",
                10.0,
                true,
                1,
                50,
                10,
                3,
                1.0,
                "B",
                5,
                0.0,
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                List.of(),
                insights,
                10.0);
        var request = new VerificationRequest("B", "q", "https://example.com/", null, null);
        var out = aggregator.aggregate(List.of(r1, rNull, r3), request);
        assertThat(out.calculationVersion()).isEqualTo("V11_GEO_PURE");
        assertThat(out.aiCitationPosition()).isEqualTo(2);
    }
}
