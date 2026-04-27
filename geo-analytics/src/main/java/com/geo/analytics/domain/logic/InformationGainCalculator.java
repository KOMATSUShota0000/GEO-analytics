package com.geo.analytics.domain.logic;

import java.lang.StrictMath;
import java.util.Objects;

/**
 * 純粋な情報利得（GEO-IG）用の静的数理カーネル。Spring や永続層に依存しない。
 *
 * <p>対数・指数は {@link StrictMath#log} / {@link StrictMath#exp} のみ。JSD は底 2（ビット）で
 * Laplace スムージング後の分布に対して定義する。
 */
public final class InformationGainCalculator {

    /** 自然対数 2。 {@link StrictMath#log} 由来。 */
    private static final double LN2 = StrictMath.log(2.0d);

    /** 事実密度の指数スカシュング係数 γ（0.1）。 */
    public static final double DENSITY_SQUASH_GAMMA = 0.1d;

    /**
     * 各面に加えて再正規化する Laplace 擬似質量。ゼロ質量のビンでも log2 が定義されるようにする。
     */
    private static final double LAPLACE_ALPHA = 1.0e-12d;

    private InformationGainCalculator() {}

    /**
     * Jensen–Shannon ダイバージェンス（底 2 = ビット）。値域は理論上 [0,1]（K 択中の多くのケース）に収まる。
     *
     * <p>中間分布 {@code M = (P_site + P_market) / 2}（スムージング後の正規化ベクトルに対して和をとり
     * 再び正規化）を用い、<br>
     * JSD = 1/2 KL(P_site||M) + 1/2 KL(P_market||M)（いずれも底 2）。
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
        int n = pSite.length;
        double[] site = laplaceSmoothAndNormalize(pSite, n);
        double[] market = laplaceSmoothAndNormalize(pMarket, n);
        double[] m = new double[n];
        double sumM = 0.0d;
        for (int i = 0; i < n; i++) {
            m[i] = 0.5d * (site[i] + market[i]);
            sumM += m[i];
        }
        for (int i = 0; i < n; i++) {
            m[i] /= sumM;
        }
        double termSite = kullbackLeiblerBits(site, m);
        double termMarket = kullbackLeiblerBits(market, m);
        double jsd = StrictMath.fma(0.5d, termSite, 0.5d * termMarket);
        if (!Double.isFinite(jsd) || jsd < 0.0d) {
            return 0.0d;
        }
        return StrictMath.min(1.0d, jsd);
    }

    /**
     * GEO-IG スコア: Q_intent * S_norm * JSD（ビット）。S_norm = 1 - exp(-0.1 * S_density)。
     *
     * <p>Q_intent は [0,1] に、S_density は非負にクランプする。
     */
    public static double geoInformationGain(double qIntent, double sDensity, double[] pSite, double[] pMarket) {
        double q = clamp01(qIntent);
        double sd = sDensity < 0.0d || !Double.isFinite(sDensity) ? 0.0d : sDensity;
        double sNorm = normalizedFactDensity(sd);
        double jsd = jensenShannonDivergenceBits(pSite, pMarket);
        return StrictMath.fma(StrictMath.fma(sNorm, jsd, 0.0d), q, 0.0d);
    }

    /**
     * S_norm = 1 - exp(-γ S_density) 。γ = {@value #DENSITY_SQUASH_GAMMA} 。
     */
    public static double normalizedFactDensity(double sDensity) {
        if (sDensity < 0.0d || !Double.isFinite(sDensity)) {
            return 0.0d;
        }
        double ex = StrictMath.exp(StrictMath.fma(-DENSITY_SQUASH_GAMMA, sDensity, 0.0d));
        return StrictMath.fma(-1.0d, ex, 1.0d);
    }

    private static double[] laplaceSmoothAndNormalize(double[] p, int n) {
        double[] out = new double[n];
        double sum = 0.0d;
        for (int i = 0; i < n; i++) {
            double v = p[i];
            if (v < 0.0d || !Double.isFinite(v)) {
                throw new IllegalArgumentException("non-finite or negative mass");
            }
            out[i] = StrictMath.fma(1.0d, v, LAPLACE_ALPHA);
            sum += out[i];
        }
        if (sum <= 0.0d || !Double.isFinite(sum)) {
            throw new IllegalArgumentException("invalid distribution sum");
        }
        for (int i = 0; i < n; i++) {
            out[i] /= sum;
        }
        return out;
    }

    /**
     * KL(P||Q) 底 2 。P・Q の各成分は正（スムージング後）を前提。ゼロ確率の寄与 0*log(0) は 0 として扱う。
     */
    private static double kullbackLeiblerBits(double[] p, double[] q) {
        double acc = 0.0d;
        for (int i = 0; i < p.length; i++) {
            double pi = p[i];
            if (pi <= 0.0d) {
                continue;
            }
            acc = StrictMath.fma(pi, log2Strict(pi) - log2Strict(q[i]), acc);
        }
        return acc;
    }

    private static double log2Strict(double x) {
        if (x <= 0.0d || !Double.isFinite(x)) {
            throw new IllegalArgumentException("log2: non-positive or non-finite");
        }
        return StrictMath.log(x) / LN2;
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
