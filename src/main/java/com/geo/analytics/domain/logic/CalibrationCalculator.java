package com.geo.analytics.domain.logic;

import java.lang.StrictMath;
import java.util.Objects;

/**
 * エージェント信頼度の較正（可換 logit 和＋Dempster 衝突ペナルティ）。DI や永続層に依存しない。
 */
public final class CalibrationCalculator {

    /** logit/シグモイド前の信頼度の下限・上限からの余白。 */
    public static final double MASS_EPSILON = 1.0e-9d;

    /** 衝突項 L の係数（正: 衝突が大きいほど log(1-K) の貢献で L が下がる）。 */
    public static final double LAMBDA_CONFLICT = 0.5d;

    /**
     * イノベーター 1 件あたりのブースト強度。実効信頼度に {@code friction * geoIgScore} を掛け合わせた量を重み付けする。
     */
    public static final double INNOVATOR_BOOST_SCALE = 2.0d;

    private static final double ONE_MINUS_MASS_EPS = 1.0d - MASS_EPSILON;

    private CalibrationCalculator() {}

    /**
     * 較正後の最終信念確率 P ∈ (0,1) を返す。
     *
     * <p>Stage: 有効信頼度 M'（クランプ＋条件付きイノベーターブースト）に対し、L_sum = sum logit(M_i')、
     * 全ペアの平均衝突度 K_tot に対し L = L_sum + LAMBDA_CONFLICT * log(1 - K_c)、P = sigmoid(L)。
     */
    public static double calibratedBelief(
            double[] agentMass, boolean[] isInnovator, double friction, double geoIgScore) {
        Objects.requireNonNull(agentMass, "agentMass");
        Objects.requireNonNull(isInnovator, "isInnovator");
        if (agentMass.length != isInnovator.length) {
            throw new IllegalArgumentException("agentMass and isInnovator must have the same length");
        }
        if (agentMass.length == 0) {
            throw new IllegalArgumentException("at least one agent is required");
        }
        int n = agentMass.length;
        double f = resolveFriction(friction);
        double g = resolveGeoIg(geoIgScore);
        double[] effective = new double[n];
        for (int i = 0; i < n; i++) {
            double m = agentMass[i];
            if (Double.isNaN(m) || m < 0.0d || m > 1.0d) {
                throw new IllegalArgumentException("agentMass out of [0,1]");
            }
            m = clampToOpenUnit(m);
            if (isInnovator[i]) {
                m = applyInnovatorBoost(m, f, g);
            }
            effective[i] = m;
        }
        double lSum = 0.0d;
        for (int i = 0; i < n; i++) {
            lSum += logit(effective[i]);
        }
        double kTot = totalPairwiseDempsterConflict(effective);
        kTot = Math.min(ONE_MINUS_MASS_EPS, Math.max(0.0d, kTot));
        double l = StrictMath.fma(LAMBDA_CONFLICT, StrictMath.log(1.0d - kTot), lSum);
        return sigmoid(l);
    }

    private static double applyInnovatorBoost(double m, double friction, double geoIg) {
        double w = StrictMath.fma(INNOVATOR_BOOST_SCALE, StrictMath.fma(friction, geoIg, 0.0d), 0.0d);
        double mShift = StrictMath.fma(m * (1.0d - m), w, m);
        return clampToOpenUnit(mShift);
    }

    private static double resolveFriction(double friction) {
        if (!Double.isFinite(friction)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, friction));
    }

    private static double resolveGeoIg(double geoIg) {
        if (!Double.isFinite(geoIg)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, geoIg));
    }

    /**
     * 全無序ペア (i, j)（i < j）について古典二元 Dempster 衝突度 K_{ij} = m_i(1-m_j) + (1-m_i) m_j の平均。
     */
    private static double totalPairwiseDempsterConflict(double[] m) {
        int n = m.length;
        if (n < 2) {
            return 0.0d;
        }
        double sum = 0.0d;
        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sum = StrictMath.fma(pairDempsterConflict(m[i], m[j]), 1.0d, sum);
                count++;
            }
        }
        return sum / (double) count;
    }

    private static double pairDempsterConflict(double mi, double mj) {
        return StrictMath.fma(mi, 1.0d - mj, StrictMath.fma(1.0d - mi, mj, 0.0d));
    }

    private static double logit(double m) {
        double num = m;
        double den = 1.0d - m;
        return StrictMath.log(num / den);
    }

    private static double sigmoid(double l) {
        return 1.0d / (1.0d + StrictMath.exp(-l));
    }

    private static double clampToOpenUnit(double v) {
        return Math.max(MASS_EPSILON, Math.min(ONE_MINUS_MASS_EPS, v));
    }
}
