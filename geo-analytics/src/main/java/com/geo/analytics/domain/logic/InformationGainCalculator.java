package com.geo.analytics.domain.logic;

import java.util.Objects;

/**
 * 議論・オンボーディング向けの軽量スコアリング。複雑な情報幾何は使わず、分布間の「ずれ」を全変動距離で測る。
 */
public final class InformationGainCalculator {

    /** 事実密度の指数スカシュング係数 γ（0.1）。 */
    public static final double DENSITY_SQUASH_GAMMA = 0.1d;

    private InformationGainCalculator() {}

    /**
     * サイト分布と市場分布の乖離を [0,1] で表す（全変動距離の半分: {@code 0.5 * Σ|P−Q|}）。
     * 値が大きいほど「話題の載せ方が市場平均から離れている」ことを意味する。
     */
    public static double jensenShannonDivergenceBits(double[] pSite, double[] pMarket) {
        Objects.requireNonNull(pSite, "pSite");
        Objects.requireNonNull(pMarket, "pMarket");
        if (pSite.length != pMarket.length) {
            throw new IllegalArgumentException("distributions must have the same length");
        }
        if (pSite.length == 0) {
            throw new IllegalArgumentException("empty distribution");
        }
        double tv = 0.0d;
        for (int i = 0; i < pSite.length; i++) {
            double a = pSite[i];
            double b = pMarket[i];
            if (a < 0.0d || b < 0.0d || !Double.isFinite(a) || !Double.isFinite(b)) {
                throw new IllegalArgumentException("non-finite or negative mass");
            }
            tv += Math.abs(a - b);
        }
        return Math.min(1.0d, 0.5d * tv);
    }

    /**
     * GEO 情報スコア（説明可能版）: {@code Q_intent × S_norm × separation}。<br>
     * {@code separation} は {@link #jensenShannonDivergenceBits}（実質 TV 距離）。
     */
    public static double geoInformationGain(double qIntent, double sDensity, double[] pSite, double[] pMarket) {
        double q = clamp01(qIntent);
        double sd = sDensity < 0.0d || !Double.isFinite(sDensity) ? 0.0d : sDensity;
        double sNorm = normalizedFactDensity(sd);
        double sep = jensenShannonDivergenceBits(pSite, pMarket);
        return Math.fma(Math.fma(sNorm, sep, 0.0d), q, 0.0d);
    }

    /**
     * S_norm = 1 - exp(-γ S_density) 。γ = {@value #DENSITY_SQUASH_GAMMA} 。
     */
    public static double normalizedFactDensity(double sDensity) {
        if (sDensity < 0.0d || !Double.isFinite(sDensity)) {
            return 0.0d;
        }
        double ex = Math.exp(Math.fma(-DENSITY_SQUASH_GAMMA, sDensity, 0.0d));
        return Math.fma(-1.0d, ex, 1.0d);
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) {
            return 0.0d;
        }
        if (x < 0.0d) {
            return 0.0d;
        }
        if (x > 1.0d) {
            return 1.0d;
        }
        return x;
    }
}
