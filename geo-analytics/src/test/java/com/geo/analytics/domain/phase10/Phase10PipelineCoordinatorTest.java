package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.gatekeeper.DictionaryGatekeeper;
import com.geo.analytics.domain.inclusion.InclusionProbabilityCalculator;
import com.geo.analytics.domain.metrics.EntropyMetricsCalculator;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Phase10PipelineCoordinatorTest {

    @Test
    void shouldThrowNullPointerExceptionWhenBrandContextIsNull() {
        DictionaryGatekeeper dictionaryGatekeeper = mock(DictionaryGatekeeper.class);
        EntropyMetricsCalculator entropyMetricsCalculator = mock(EntropyMetricsCalculator.class);
        SemanticJudgmentEngine semanticJudgmentEngine = mock(SemanticJudgmentEngine.class);
        InclusionProbabilityCalculator inclusionProbabilityCalculator = mock(InclusionProbabilityCalculator.class);
        Phase10PipelineCoordinator coordinator =
                new Phase10PipelineCoordinator(
                        dictionaryGatekeeper,
                        entropyMetricsCalculator,
                        semanticJudgmentEngine,
                        inclusionProbabilityCalculator);
        assertThatThrownBy(() -> coordinator.executePipeline(null, "raw"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenRawTextIsNull() {
        DictionaryGatekeeper dictionaryGatekeeper = mock(DictionaryGatekeeper.class);
        EntropyMetricsCalculator entropyMetricsCalculator = mock(EntropyMetricsCalculator.class);
        SemanticJudgmentEngine semanticJudgmentEngine = mock(SemanticJudgmentEngine.class);
        InclusionProbabilityCalculator inclusionProbabilityCalculator = mock(InclusionProbabilityCalculator.class);
        Phase10PipelineCoordinator coordinator =
                new Phase10PipelineCoordinator(
                        dictionaryGatekeeper,
                        entropyMetricsCalculator,
                        semanticJudgmentEngine,
                        inclusionProbabilityCalculator);
        Phase10PipelineCoordinator.BrandContext brandContext =
                new Phase10PipelineCoordinator.BrandContext("id", "ref");
        assertThatThrownBy(() -> coordinator.executePipeline(brandContext, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldSkipEntropyAndSemanticWhenDictionaryHitAndPassDummyMetricsToInclusion() {
        DictionaryGatekeeper dictionaryGatekeeper = mock(DictionaryGatekeeper.class);
        EntropyMetricsCalculator entropyMetricsCalculator = mock(EntropyMetricsCalculator.class);
        SemanticJudgmentEngine semanticJudgmentEngine = mock(SemanticJudgmentEngine.class);
        InclusionProbabilityCalculator inclusionProbabilityCalculator = mock(InclusionProbabilityCalculator.class);
        Phase10PipelineCoordinator coordinator =
                new Phase10PipelineCoordinator(
                        dictionaryGatekeeper,
                        entropyMetricsCalculator,
                        semanticJudgmentEngine,
                        inclusionProbabilityCalculator);
        String raw = "raw-text";
        String brandRef = "\u30ad\u30e4\u30ce\u30f3ref";
        Phase10PipelineCoordinator.BrandContext brandContext =
                new Phase10PipelineCoordinator.BrandContext("brand-1", brandRef);
        DictionaryGatekeeper.GatekeeperResult gatekeeperResult =
                new DictionaryGatekeeper.GatekeeperResult(true, 1.0, true, "\u30ad\u30e4\u30ce\u30f3");
        when(dictionaryGatekeeper.evaluate(raw)).thenReturn(gatekeeperResult);
        when(inclusionProbabilityCalculator.computePib(
                        eq(gatekeeperResult),
                        eq(new EntropyMetricsCalculator.EntropyMetrics(0.0d, 0.0d, 0)),
                        eq(
                                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure(
                                        "Skipped due to dictionary hit")),
                        eq(brandRef)))
                .thenReturn(1.0);
        double result = coordinator.executePipeline(brandContext, raw);
        assertThat(result).isEqualTo(1.0);
        verify(entropyMetricsCalculator, never()).compute(any());
        verify(semanticJudgmentEngine, never()).evaluate(any());
        ArgumentCaptor<String> brandReferenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(inclusionProbabilityCalculator)
                .computePib(
                        eq(gatekeeperResult),
                        eq(new EntropyMetricsCalculator.EntropyMetrics(0.0d, 0.0d, 0)),
                        eq(
                                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure(
                                        "Skipped due to dictionary hit")),
                        brandReferenceCaptor.capture());
        assertThat(brandReferenceCaptor.getValue()).isEqualTo(brandContext.brandReferenceNormalized());
    }

    @Test
    void shouldRunFullPipelineWhenDictionaryMissesAndForwardResultsToInclusion() {
        DictionaryGatekeeper dictionaryGatekeeper = mock(DictionaryGatekeeper.class);
        EntropyMetricsCalculator entropyMetricsCalculator = mock(EntropyMetricsCalculator.class);
        SemanticJudgmentEngine semanticJudgmentEngine = mock(SemanticJudgmentEngine.class);
        InclusionProbabilityCalculator inclusionProbabilityCalculator = mock(InclusionProbabilityCalculator.class);
        Phase10PipelineCoordinator coordinator =
                new Phase10PipelineCoordinator(
                        dictionaryGatekeeper,
                        entropyMetricsCalculator,
                        semanticJudgmentEngine,
                        inclusionProbabilityCalculator);
        String raw = "raw-miss";
        String normalized = "\u682a\u5f0f\u4f1a\u793e\u30bd\u30cb\u30fc";
        String brandRef = "\u30bd\u30cb\u30fc";
        Phase10PipelineCoordinator.BrandContext brandContext =
                new Phase10PipelineCoordinator.BrandContext("brand-2", brandRef);
        DictionaryGatekeeper.GatekeeperResult gatekeeperResult =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, normalized);
        when(dictionaryGatekeeper.evaluate(raw)).thenReturn(gatekeeperResult);
        EntropyMetricsCalculator.EntropyMetrics entropyMetrics =
                new EntropyMetricsCalculator.EntropyMetrics(4.0, 1.0, 4);
        when(entropyMetricsCalculator.compute(normalized)).thenReturn(entropyMetrics);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semanticOutcome =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 0.88));
        when(semanticJudgmentEngine.evaluate(normalized)).thenReturn(semanticOutcome);
        when(inclusionProbabilityCalculator.computePib(
                        eq(gatekeeperResult), eq(entropyMetrics), eq(semanticOutcome), eq(brandRef)))
                .thenReturn(0.77);
        double result = coordinator.executePipeline(brandContext, raw);
        assertThat(result).isCloseTo(0.77, within(1e-9));
        verify(entropyMetricsCalculator).compute(eq(normalized));
        verify(semanticJudgmentEngine).evaluate(eq(normalized));
        ArgumentCaptor<DictionaryGatekeeper.GatekeeperResult> gatekeeperCaptor =
                ArgumentCaptor.forClass(DictionaryGatekeeper.GatekeeperResult.class);
        ArgumentCaptor<EntropyMetricsCalculator.EntropyMetrics> entropyCaptor =
                ArgumentCaptor.forClass(EntropyMetricsCalculator.EntropyMetrics.class);
        ArgumentCaptor<SemanticJudgmentEngine.SemanticJudgmentOutcome> semanticCaptor =
                ArgumentCaptor.forClass(SemanticJudgmentEngine.SemanticJudgmentOutcome.class);
        ArgumentCaptor<String> brandReferenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(inclusionProbabilityCalculator)
                .computePib(
                        gatekeeperCaptor.capture(),
                        entropyCaptor.capture(),
                        semanticCaptor.capture(),
                        brandReferenceCaptor.capture());
        assertThat(gatekeeperCaptor.getValue()).isEqualTo(gatekeeperResult);
        assertThat(entropyCaptor.getValue()).isEqualTo(entropyMetrics);
        assertThat(semanticCaptor.getValue()).isEqualTo(semanticOutcome);
        assertThat(brandReferenceCaptor.getValue()).isEqualTo(brandContext.brandReferenceNormalized());
    }
}
