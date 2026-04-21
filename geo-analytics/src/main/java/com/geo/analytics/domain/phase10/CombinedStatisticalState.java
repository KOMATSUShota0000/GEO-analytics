package com.geo.analytics.domain.phase10;

public record CombinedStatisticalState(WelfordVarianceState welford, PSquareQuantileState psSquare) {

    public static CombinedStatisticalState empty() {
        return new CombinedStatisticalState(WelfordVarianceState.empty(), PSquareQuantileState.empty());
    }
}
