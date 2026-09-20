package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.CompetitorResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitorShareAggregatorTest {

    private static CompetitorResult competitor(String label, double som) {
        return new CompetitorResult(label, som, 1, 1);
    }

    @Test
    void sharesAreSomRatioAcrossTheWholeAnalysis() {
        var shares = CompetitorShareAggregator.aggregate(
                "自社",
                List.of(20.0, 12.0),
                List.of(List.of(competitor("A社", 40.0)), List.of(competitor("A社", 8.0), competitor("B社", 20.0))));

        assertThat(shares).hasSize(3);
        assertThat(shares.getFirst().label()).isEqualTo("自社");
        assertThat(shares.getFirst().self()).isTrue();
        assertThat(shares.getFirst().share()).isEqualTo(32.0);
        assertThat(shares.get(1).label()).isEqualTo("A社");
        assertThat(shares.get(1).share()).isEqualTo(48.0);
        assertThat(shares.get(2).label()).isEqualTo("B社");
        assertThat(shares.get(2).share()).isEqualTo(20.0);
    }

    @Test
    void competitorsBeyondTheSliceLimitAreFoldedIntoOthers() {
        var perQuery = List.of(List.of(
                competitor("A", 30.0),
                competitor("B", 25.0),
                competitor("C", 20.0),
                competitor("D", 15.0),
                competitor("E", 10.0),
                competitor("F", 5.0),
                competitor("G", 5.0)));

        var shares = CompetitorShareAggregator.aggregate("自社", List.of(10.0), perQuery);

        assertThat(shares).hasSize(CompetitorShareAggregator.MAX_COMPETITOR_SLICES + 2);
        assertThat(shares.getLast().label()).isEqualTo(CompetitorShareAggregator.OTHERS_LABEL);
        assertThat(shares.stream().mapToDouble(s -> s.share()).sum()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void nullAndZeroScoresAreIgnoredWithoutBreakingTheDenominator() {
        var shares = CompetitorShareAggregator.aggregate(
                "自社",
                Arrays.asList(10.0, null, 0.0),
                List.of(List.of(competitor("A社", 10.0), new CompetitorResult("欠測", null, null, 0))));

        assertThat(shares).hasSize(2);
        assertThat(shares.getFirst().share()).isEqualTo(50.0);
        assertThat(shares.get(1).label()).isEqualTo("A社");
    }

    @Test
    void emptyInputYieldsNoSlices() {
        assertThat(CompetitorShareAggregator.aggregate("自社", List.of(), List.of())).isEmpty();
        assertThat(CompetitorShareAggregator.aggregate("自社", null, null)).isEmpty();
    }
}
