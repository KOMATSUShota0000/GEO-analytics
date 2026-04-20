package com.geo.analytics.domain.phase10;

import com.geo.analytics.domain.gatekeeper.DictionaryGatekeeper;
import com.geo.analytics.domain.inclusion.InclusionProbabilityCalculator;
import com.geo.analytics.domain.metrics.EntropyMetricsCalculator;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine;
import java.lang.ScopedValue;
import java.util.Objects;

public final class Phase10PipelineCoordinator {

    public record BrandContext(String brandId, String brandReferenceNormalized) {
    }

    public static final ScopedValue<BrandContext> BRAND_CONTEXT = ScopedValue.newInstance();

    private final DictionaryGatekeeper dictionaryGatekeeper;

    private final EntropyMetricsCalculator entropyMetricsCalculator;

    private final SemanticJudgmentEngine semanticJudgmentEngine;

    private final InclusionProbabilityCalculator inclusionProbabilityCalculator;

    public Phase10PipelineCoordinator(
            DictionaryGatekeeper dictionaryGatekeeper,
            EntropyMetricsCalculator entropyMetricsCalculator,
            SemanticJudgmentEngine semanticJudgmentEngine,
            InclusionProbabilityCalculator inclusionProbabilityCalculator) {
        this.dictionaryGatekeeper = Objects.requireNonNull(dictionaryGatekeeper);
        this.entropyMetricsCalculator = Objects.requireNonNull(entropyMetricsCalculator);
        this.semanticJudgmentEngine = Objects.requireNonNull(semanticJudgmentEngine);
        this.inclusionProbabilityCalculator = Objects.requireNonNull(inclusionProbabilityCalculator);
    }

    public double executePipeline(BrandContext brandContext, String rawText) {
        Objects.requireNonNull(brandContext);
        Objects.requireNonNull(rawText);
        return ScopedValue.where(BRAND_CONTEXT, brandContext)
                .call(() -> {
                    DictionaryGatekeeper.GatekeeperResult gatekeeperResult = dictionaryGatekeeper.evaluate(rawText);
                    EntropyMetricsCalculator.EntropyMetrics entropy;
                    SemanticJudgmentEngine.SemanticJudgmentOutcome semantic;
                    if (gatekeeperResult.dictionaryHit()) {
                        entropy = new EntropyMetricsCalculator.EntropyMetrics(0.0d, 0.0d, 0);
                        semantic = new SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure("Skipped due to dictionary hit");
                    } else {
                        entropy = entropyMetricsCalculator.compute(gatekeeperResult.normalizedText());
                        semantic = semanticJudgmentEngine.evaluate(gatekeeperResult.normalizedText());
                    }
                    String brandReference = BRAND_CONTEXT.get().brandReferenceNormalized();
                    return inclusionProbabilityCalculator.computePib(gatekeeperResult, entropy, semantic, brandReference);
                });
    }
}