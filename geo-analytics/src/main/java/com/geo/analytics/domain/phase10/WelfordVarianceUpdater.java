package com.geo.analytics.domain.phase10;

public final class WelfordVarianceUpdater {

    private WelfordVarianceUpdater() {
    }

    public static WelfordVarianceState update(WelfordVarianceState state, double newValue) {
        long newCount = state.count() + 1L;
        double delta = newValue - state.mean();
        double newMean = StrictMath.fma(delta, 1.0d / newCount, state.mean());
        double delta2 = newValue - newMean;
        double newM2 = StrictMath.max(0.0d, StrictMath.fma(delta, delta2, state.m2()));
        return new WelfordVarianceState(newCount, newMean, newM2);
    }
}
