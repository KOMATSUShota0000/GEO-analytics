package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CalibrationCalculatorTest {

    private static final double TOL = 1.0e-12d;

    @Test
    void calibratedBeliefIsCommutativeUnderJointShuffleOfMassAndFlags() {
        double[] mass = {0.2d, 0.7d, 0.4d, 0.6d};
        boolean[] innov = {true, false, true, false};
        double base =
                CalibrationCalculator.calibratedBelief(mass, innov, 0.6d, 0.5d);
        Random random = new Random(0xDECAFBADCAFEL);
        for (int round = 0; round < 20; round++) {
            int n = mass.length;
            List<Integer> perm = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                perm.add(i);
            }
            Collections.shuffle(perm, random);
            double[] m2 = new double[n];
            boolean[] b2 = new boolean[n];
            for (int i = 0; i < n; i++) {
                int j = perm.get(i);
                m2[i] = mass[j];
                b2[i] = innov[j];
            }
            double sh =
                    CalibrationCalculator.calibratedBelief(m2, b2, 0.6d, 0.5d);
            assertThat(sh).isCloseTo(base, within(TOL));
        }
    }

    @Test
    void innovatorWithHighFrictionAndGeoIgBoostsBelief() {
        double[] m = {0.35d, 0.5d, 0.45d};
        boolean[] noInn = {false, false, false};
        boolean[] oneInn = {true, false, false};
        double f = 0.95d;
        double g = 0.95d;
        double pBase = CalibrationCalculator.calibratedBelief(m, noInn, f, g);
        double pBoosted = CalibrationCalculator.calibratedBelief(m, oneInn, f, g);
        assertThat(pBoosted).isGreaterThan(pBase);
    }

    @Test
    void highConflictingOpinionsLowerBelief() {
        boolean[] f = {false, false};
        double fFriction = 0.5d;
        double fGeo = 0.5d;
        double pConflict = CalibrationCalculator.calibratedBelief(new double[] {0.9d, 0.1d}, f, fFriction, fGeo);
        double pMild = CalibrationCalculator.calibratedBelief(new double[] {0.5d, 0.5d}, f, fFriction, fGeo);
        assertThat(pConflict).isLessThan(pMild);
    }

    @Test
    void twoIdenticalMassesWithZeroFrictionMatchesSigmoidOfSumOfTwoLogits() {
        double[] m = {0.5d, 0.5d};
        boolean[] inv = {false, false};
        double p = CalibrationCalculator.calibratedBelief(m, inv, 0.0d, 0.0d);
        double lSum =
                StrictMath.log(0.5d / 0.5d) + StrictMath.log(0.5d / 0.5d);
        double l =
                lSum
                        + CalibrationCalculator.LAMBDA_CONFLICT
                        * StrictMath.log(1.0d - 0.5d);
        double ex = 1.0d / (1.0d + StrictMath.exp(-l));
        assertThat(p).isCloseTo(ex, within(1.0e-9d));
    }

    @Test
    void geoIgAndFrictionAtZeroGivesNoInnovatorChangeVersusAllFalse() {
        double[] m = {0.3d, 0.4d, 0.5d};
        boolean[] allFalse = {false, false, false};
        boolean[] oneTrue = {true, false, false};
        double p0 = CalibrationCalculator.calibratedBelief(m, allFalse, 0.0d, 0.0d);
        double p1 = CalibrationCalculator.calibratedBelief(m, oneTrue, 0.0d, 0.0d);
        assertThat(p0).isCloseTo(p1, within(1.0e-12d));
    }
}
