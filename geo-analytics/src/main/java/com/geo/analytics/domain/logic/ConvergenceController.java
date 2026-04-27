package com.geo.analytics.domain.logic;

import java.lang.StrictMath;
import java.util.Arrays;
import java.util.Objects;

/**
 * 議論の統計的収束を検知し、停止候補を返す純粋 static カーネル。Spring / 永続層に非依存。
 */
public final class ConvergenceController {

    public static final double BASE_ALPHA = 0.05d;
    public static final double TURN_LAMBDA = 0.2d;
    public static final double FRICTION_OFFSET = 0.1d;
    public static final double COSINE_EPSILON = 1.0e-9d;

    /**
     * {@link #shouldStop} と同式で監査用に 1D Wasserstein、閾値、情報幾何ドリフトを取得する。 friction が
     * 非有限のとき各値は {@link Double#NaN}（{@link #shouldStop} との一貫性）。
     */
    public record ConvergenceSnapshot(double wasserstein1, double threshold, double informationGeometryDrift) {}

    private ConvergenceController() {}

    /**
     * 停止の推奨: {@code wasserstein1D < threshold} のとき {@code true}。非有限の場合は安全のため
     * {@code false}。
     *
     * <p>閾値: {@code BaseAlpha * (1 + IG_drift) * exp(-λ·turn) * 1/(friction + ε)} 。
     */
    public static boolean shouldStop(
            double[] prevConfidences,
            double[] currConfidences,
            double[] prevCentroid,
            double[] currCentroid,
            double friction,
            int turnCount) {
        Objects.requireNonNull(prevConfidences, "prevConfidences");
        Objects.requireNonNull(currConfidences, "currConfidences");
        Objects.requireNonNull(prevCentroid, "prevCentroid");
        Objects.requireNonNull(currCentroid, "currCentroid");
        if (prevConfidences.length != currConfidences.length) {
            throw new IllegalArgumentException("prevConfidences and currConfidences must have the same length");
        }
        if (prevConfidences.length == 0) {
            throw new IllegalArgumentException("empty confidences");
        }
        if (prevCentroid.length != currCentroid.length) {
            throw new IllegalArgumentException("centroids must have the same dimension");
        }
        if (prevCentroid.length == 0) {
            throw new IllegalArgumentException("empty centroids");
        }
        if (!Double.isFinite(friction)) {
            return false;
        }
        int turn = turnCount < 0 ? 0 : turnCount;
        double w1 = wasserstein1DEqualWeight(prevConfidences, currConfidences);
        double igDrift = informationGeometryDrift(prevCentroid, currCentroid);
        double th = computeThreshold(igDrift, friction, turn);
        if (!Double.isFinite(w1) || !Double.isFinite(th)) {
            return false;
        }
        return w1 < th;
    }

    /**
     * 監査用: 前・現ターンの信頼度分布と中心ベクトルから、1D W1・閾値・IG ドリフトを計算する。入力検証は
     * {@link #shouldStop} に準拠。 friction が非有限のとき 3 成分とも {@link Double#NaN}。
     */
    public static ConvergenceSnapshot computeConvergenceSnapshot(
            double[] prevConfidences,
            double[] currConfidences,
            double[] prevCentroid,
            double[] currCentroid,
            double friction,
            int turnCount) {
        Objects.requireNonNull(prevConfidences, "prevConfidences");
        Objects.requireNonNull(currConfidences, "currConfidences");
        Objects.requireNonNull(prevCentroid, "prevCentroid");
        Objects.requireNonNull(currCentroid, "currCentroid");
        if (prevConfidences.length != currConfidences.length) {
            throw new IllegalArgumentException("prevConfidences and currConfidences must have the same length");
        }
        if (prevConfidences.length == 0) {
            throw new IllegalArgumentException("empty confidences");
        }
        if (prevCentroid.length != currCentroid.length) {
            throw new IllegalArgumentException("centroids must have the same dimension");
        }
        if (prevCentroid.length == 0) {
            throw new IllegalArgumentException("empty centroids");
        }
        if (!Double.isFinite(friction)) {
            return new ConvergenceSnapshot(Double.NaN, Double.NaN, Double.NaN);
        }
        int turn = turnCount < 0 ? 0 : turnCount;
        double w1 = wasserstein1DEqualWeight(prevConfidences, currConfidences);
        double igDrift = informationGeometryDrift(prevCentroid, currCentroid);
        double th = computeThreshold(igDrift, friction, turn);
        return new ConvergenceSnapshot(w1, th, igDrift);
    }

    /**
     * 1 次元等重み Wasserstein-1: コピー昇順ソート後、対応分の絶対差の平均。
     */
    public static double wasserstein1DEqualWeight(double[] p, double[] q) {
        Objects.requireNonNull(p, "p");
        Objects.requireNonNull(q, "q");
        if (p.length != q.length) {
            throw new IllegalArgumentException("same length required");
        }
        int n = p.length;
        if (n == 0) {
            return Double.NaN;
        }
        double[] a = Arrays.copyOf(p, n);
        double[] b = Arrays.copyOf(q, n);
        Arrays.sort(a);
        Arrays.sort(b);
        double sum = 0.0d;
        for (int i = 0; i < n; i++) {
            sum = StrictMath.fma(1.0d, StrictMath.abs(a[i] - b[i]), sum);
        }
        return sum / (double) n;
    }

    static double informationGeometryDrift(double[] prev, double[] curr) {
        double sim = cosineSimilarity(prev, curr);
        double c = sim;
        if (c < -1.0d) {
            c = -1.0d;
        } else if (c > 1.0d) {
            c = 1.0d;
        }
        return 1.0d - c;
    }

    static double computeThreshold(double igDrift, double friction, int turn) {
        double turnFactor = StrictMath.exp(StrictMath.fma(-TURN_LAMBDA, (double) turn, 0.0d));
        double denom = StrictMath.fma(1.0d, friction, FRICTION_OFFSET);
        if (denom <= 0.0d || !Double.isFinite(denom)) {
            return Double.NaN;
        }
        return StrictMath.fma(
                StrictMath.fma(BASE_ALPHA, 1.0d + igDrift, 0.0d),
                turnFactor * (1.0d / denom),
                0.0d);
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0d;
        double na = 0.0d;
        double nb = 0.0d;
        for (int i = 0; i < a.length; i++) {
            double x = a[i];
            double y = b[i];
            dot = StrictMath.fma(x, y, dot);
            na = StrictMath.fma(x, x, na);
            nb = StrictMath.fma(y, y, nb);
        }
        double den = StrictMath.fma(StrictMath.sqrt(na), StrictMath.sqrt(nb), COSINE_EPSILON);
        if (den <= 0.0d || !Double.isFinite(den)) {
            return Double.NaN;
        }
        return dot / den;
    }
}
