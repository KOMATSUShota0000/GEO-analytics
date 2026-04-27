package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

class ConvergenceControllerTest {

    @Test
    void identicalDistributionsGivesW1ZeroAndShouldStopWhenThresholdPositive() {
        double[] c = {0.1d, 0.2d, 0.3d, 0.4d};
        double[] z = {1.0d, 0.0d, 0.0d};
        assertThat(ConvergenceController.wasserstein1DEqualWeight(c, c.clone())).isZero();
        boolean stop =
                ConvergenceController.shouldStop(
                        c, c.clone(), z.clone(), z.clone(), 0.0d, 0);
        assertThat(stop).isTrue();
    }

    @Test
    void nonFiniteFrictionReturnsFalseWithoutThrowing() {
        double[] conf = {0.25d, 0.25d, 0.25d, 0.25d};
        double[] ctr = {0.5d, 0.0d};
        assertThatCode(
                () ->
                        ConvergenceController.shouldStop(
                                conf, conf.clone(), ctr, ctr.clone(), Double.NaN, 0))
                .doesNotThrowAnyException();
        assertThat(
                ConvergenceController.shouldStop(
                        conf, conf.clone(), ctr, ctr.clone(), Double.NaN, 0))
                .isFalse();
    }

    @Test
    void turnPressureDecreasesThresholdSoSameW1MayFlipFromTrueToFalse() {
        double ig = 0.0d;
        double f = 0.0d;
        double t0 = ConvergenceController.computeThreshold(ig, f, 0);
        double t10 = ConvergenceController.computeThreshold(ig, f, 10);
        assertThat(t10).isLessThan(t0);
        assertThat(t0).isPositive();
        assertThat(t10).isPositive();
    }

    @Test
    void sameConfidencesWithHigherTurnMakesStopHarderForPositiveW1() {
        double[] a = {0.0d, 1.0d};
        double[] b = {0.1d, 0.9d};
        double[] cent = {1.0d, 0.0d, 0.0d};
        double w1 = ConvergenceController.wasserstein1DEqualWeight(a, b);
        assertThat(w1).isPositive();
        assertThat(ConvergenceController.computeThreshold(0.0d, 0.2d, 0))
                .isGreaterThan(ConvergenceController.computeThreshold(0.0d, 0.2d, 50));
        boolean lowTurn = ConvergenceController.shouldStop(a, b, cent, cent.clone(), 0.2d, 0);
        boolean highTurn = ConvergenceController.shouldStop(a, b, cent, cent.clone(), 0.2d, 50);
        assertThat(lowTurn).isTrue();
        assertThat(highTurn).isFalse();
    }

    @Test
    void orthogonalCentroidsMaximizeDriftVersusCollinear() {
        double[] parPrev = {1.0d, 0.0d, 0.0d};
        double[] parCurr = {1.0d, 0.0d, 0.0d};
        double[] orthPrev = {1.0d, 0.0d, 0.0d};
        double[] orthCurr = {0.0d, 1.0d, 0.0d};
        double dPar = ConvergenceController.informationGeometryDrift(parPrev, parCurr);
        double dOrth = ConvergenceController.informationGeometryDrift(orthPrev, orthCurr);
        assertThat(dPar).isCloseTo(0.0d, offset(1.0e-8d));
        assertThat(dOrth).isEqualTo(1.0d);
        double tPar = ConvergenceController.computeThreshold(dPar, 0.0d, 0);
        double tOrth = ConvergenceController.computeThreshold(dOrth, 0.0d, 0);
        assertThat(tOrth).isGreaterThan(tPar);
    }

    @Test
    void confidenceLengthMismatchThrows() {
        assertThatThrownBy(
                () ->
                        ConvergenceController.shouldStop(
                                new double[] {1.0d},
                                new double[] {0.5d, 0.5d},
                                new double[] {0.0d},
                                new double[] {0.0d},
                                0.0d,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computeConvergenceSnapshotMatchesW1AndThreshold() {
        double[] a = {0.1d, 0.2d, 0.3d, 0.4d};
        double[] b = {0.2d, 0.1d, 0.2d, 0.5d};
        double[] p = {1.0d, 0.0d, 0.0d};
        double[] q = {0.0d, 1.0d, 0.0d};
        double w1 = ConvergenceController.wasserstein1DEqualWeight(a, b);
        double d = ConvergenceController.informationGeometryDrift(p, q);
        double th = ConvergenceController.computeThreshold(d, 0.2d, 1);
        ConvergenceController.ConvergenceSnapshot snap =
                ConvergenceController.computeConvergenceSnapshot(a, b, p, q, 0.2d, 1);
        assertThat(snap.wasserstein1()).isEqualTo(w1);
        assertThat(snap.informationGeometryDrift()).isEqualTo(d);
        assertThat(snap.threshold()).isEqualTo(th);
    }
}
