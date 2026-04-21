package com.geo.analytics.domain.phase10;

public final class PSquarePredictor {

    private PSquarePredictor() {
    }

    public static double parabolic(double qIm1, double nIm1, double qI, double nI, double qIp1, double nIp1, int d) {
        if (d != 1 && d != -1) {
            throw new IllegalArgumentException("d must be 1 or -1");
        }
        double dnLeft = nI - nIm1;
        double dnRight = nIp1 - nI;
        if (dnLeft <= 0.0d || dnRight <= 0.0d) {
            throw new IllegalArgumentException("Invalid marker positions");
        }
        double dnSpan = nIp1 - nIm1;
        double slopeLeft = (qI - qIm1) / dnLeft;
        double slopeRight = (qIp1 - qI) / dnRight;
        double c1 = dnLeft + d;
        double c2 = dnRight - d;
        double inner = StrictMath.fma(c1, slopeRight, StrictMath.fma(c2, slopeLeft, 0.0d));
        return StrictMath.fma((double) d / dnSpan, inner, qI);
    }

    public static double linear(double qI, double nI, double qAtD, double nAtD, int d) {
        if (d != 1 && d != -1) {
            throw new IllegalArgumentException("d must be 1 or -1");
        }
        if (nAtD == nI) {
            throw new IllegalArgumentException("Invalid marker positions");
        }
        double dn = nAtD - nI;
        return StrictMath.fma((double) d, (qAtD - qI) / dn, qI);
    }
}
