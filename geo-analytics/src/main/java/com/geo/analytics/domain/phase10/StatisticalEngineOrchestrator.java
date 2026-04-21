package com.geo.analytics.domain.phase10;

import java.util.concurrent.atomic.AtomicReference;

public final class StatisticalEngineOrchestrator {

    private final AtomicReference<CombinedStatisticalState> stateRef;

    public StatisticalEngineOrchestrator() {
        this.stateRef = new AtomicReference<>(CombinedStatisticalState.empty());
    }

    public CombinedStatisticalState state() {
        return stateRef.get();
    }

    public OrchestrationResult process(int rank, double pib, CliffModelCalculator.Bm25Parameters params,
            double lambda) {
        double slabScore = CliffModelCalculator.slabScore(rank, pib, params);
        return updateAndCheck(slabScore, lambda);
    }

    public OrchestrationResult updateAndCheck(double slabScore, double psquareLambda) {
        while (true) {
            CombinedStatisticalState current = stateRef.get();
            if (ZScorePurgeFilter.shouldPurge(current.psSquare(), slabScore)) {
                return new OrchestrationResult(true, slabScore);
            }
            WelfordVarianceState nextWelford = WelfordVarianceUpdater.update(current.welford(), slabScore);
            PSquareQuantileState nextPSquare = PSquareQuantileUpdater.update(current.psSquare(), slabScore,
                    psquareLambda);
            CombinedStatisticalState nextCombined = new CombinedStatisticalState(nextWelford, nextPSquare);
            if (stateRef.compareAndSet(current, nextCombined)) {
                return new OrchestrationResult(false, slabScore);
            }
            Thread.onSpinWait();
        }
    }
}
