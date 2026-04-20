package com.geo.analytics.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class EntropyMetricsCalculatorTest {

    private final EntropyMetricsCalculator calculator = new EntropyMetricsCalculator();

    @Test
    void shouldReturnZeroMetricsForNullInput() {
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute(null);
        assertThat(metrics.codePointCount()).isEqualTo(0);
        assertThat(metrics.effectiveLength()).isEqualTo(0.0);
        assertThat(metrics.entropyDensity()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroMetricsForEmptyString() {
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute("");
        assertThat(metrics.codePointCount()).isEqualTo(0);
        assertThat(metrics.effectiveLength()).isEqualTo(0.0);
        assertThat(metrics.entropyDensity()).isEqualTo(0.0);
    }

    @Test
    void shouldWeightAllKanjiAtTenPerCodePoint() {
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute("\u682a\u5f0f\u4f1a\u793e");
        assertThat(metrics.codePointCount()).isEqualTo(4);
        assertThat(metrics.effectiveLength()).isEqualTo(4.0);
        assertThat(metrics.entropyDensity()).isEqualTo(1.0);
    }

    @Test
    void shouldTreatKatakanaAndLongVowelMarkAtWeightSix() {
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute("\u30bd\u30cb\u30fc");
        assertThat(metrics.codePointCount()).isEqualTo(3);
        assertThat(metrics.effectiveLength()).isCloseTo(1.8, within(1e-15));
        assertThat(metrics.entropyDensity()).isCloseTo(0.6, within(1e-15));
    }

    @Test
    void shouldWeightAsciiAlphanumericAndPunctuationAtFour() {
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute("Sony Corp.");
        assertThat(metrics.codePointCount()).isEqualTo(10);
        assertThat(metrics.effectiveLength()).isEqualTo(4.0);
        assertThat(metrics.entropyDensity()).isCloseTo(0.4, within(1e-15));
    }

    @Test
    void shouldAggregateMixedScriptsForKatakanaParensAndKanjiStockMark() {
        EntropyMetricsCalculator.EntropyMetrics metrics =
                calculator.compute("\u30ad\u30e4\u30ce\u30f3(\u682a)");
        assertThat(metrics.codePointCount()).isEqualTo(7);
        assertThat(metrics.effectiveLength()).isCloseTo(4.2, within(1e-15));
        assertThat(metrics.entropyDensity()).isCloseTo(0.6, within(1e-14));
    }

    @Test
    void shouldCountSupplementaryIdeographOnceNotTwiceAsChars() {
        String input = "\uD842\uDFB7\u91CE\u5BB6";
        assertThat(input.length()).isEqualTo(4);
        EntropyMetricsCalculator.EntropyMetrics metrics = calculator.compute(input);
        assertThat(metrics.codePointCount()).isEqualTo(3);
        assertThat(metrics.effectiveLength()).isEqualTo(3.0);
        assertThat(metrics.entropyDensity()).isEqualTo(1.0);
    }
}
