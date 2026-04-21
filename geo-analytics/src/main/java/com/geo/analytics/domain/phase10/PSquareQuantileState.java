package com.geo.analytics.domain.phase10;

public record PSquareQuantileState(long count, double q1, double q2, double q3, double q4, double q5,
        double n1, double n2, double n3, double n4, double n5, double nPrime1, double nPrime2,
        double nPrime3, double nPrime4, double nPrime5) {

    public static PSquareQuantileState empty() {
        return new PSquareQuantileState(0L, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d,
                0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
    }

    public double median() {
        return q3;
    }

    public double iqr() {
        return StrictMath.max(0.0d, q4 - q2);
    }
}
