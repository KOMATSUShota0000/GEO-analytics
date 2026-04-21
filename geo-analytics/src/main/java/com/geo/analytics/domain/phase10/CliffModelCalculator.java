package com.geo.analytics.domain.phase10;

public final class CliffModelCalculator {

    public static final int R_SKIM_MAX = 3;

    public static final double P_SKIM = 0.8d;

    public static final double P_EXIT = 0.4d;

    public static final double K1 = 1.2d;

    public static final double B = 0.75d;

    public record Bm25Parameters(double tf, double dl, double avgdl, double idf) {
    }

    private CliffModelCalculator() {
    }

    public static double persistenceProbability(int rank) {
        if (rank <= 0) {
            throw new IllegalArgumentException();
        }
        if (rank <= R_SKIM_MAX) {
            return P_SKIM;
        }
        return P_EXIT;
    }

    public static double rankDecayFactor(int rank) {
        return StrictMath.pow(persistenceProbability(rank), rank - 1);
    }

    public static double modifiedBm25(Bm25Parameters params) {
        double dlOverAvgdl = params.dl() / params.avgdl();
        double lengthNorm = StrictMath.fma(B, dlOverAvgdl, 1.0d - B);
        double denom = StrictMath.fma(K1, lengthNorm, params.tf());
        double numer = StrictMath.fma(params.tf(), K1, params.tf());
        return StrictMath.fma(params.idf(), numer / denom, 0.0d);
    }

    public static double slabScore(int rank, double inclusionProbability, Bm25Parameters params) {
        return StrictMath.fma(StrictMath.fma(inclusionProbability, rankDecayFactor(rank), 0.0d),
                modifiedBm25(params), 0.0d);
    }
}
