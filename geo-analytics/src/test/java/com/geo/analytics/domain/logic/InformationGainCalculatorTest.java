package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class InformationGainCalculatorTest {

    private static final double TIGHT = 1.0e-9d;

    @Test
    void jsdIsZeroWhenSiteAndMarketAreIdentical() {
        double[] p = {0.2d, 0.3d, 0.5d};
        double jsd = InformationGainCalculator.jensenShannonDivergenceBits(p, p.clone());
        assertThat(jsd).isZero();
    }

    @Test
    void jsdApproachesOneBitWhenDistributionsAreDisjointSupport() {
        double[] site = {1.0d, 0.0d};
        double[] market = {0.0d, 1.0d};
        double jsd = InformationGainCalculator.jensenShannonDivergenceBits(site, market);
        assertThat(jsd).isCloseTo(1.0d, within(1.0e-6d));
        assertThat(jsd).isLessThanOrEqualTo(1.0d);
    }

    @Test
    void squashedDensityDoesNotExceedOneForExtremeDensity() {
        double sNorm = InformationGainCalculator.normalizedFactDensity(1000.0d);
        assertThat(sNorm).isLessThanOrEqualTo(1.0d);
        assertThat(sNorm).isGreaterThan(0.9999d);
        assertThat(Double.isFinite(sNorm)).isTrue();
        double score =
                InformationGainCalculator.geoInformationGain(
                        1.0d, 1000.0d, new double[] {1.0d, 0.0d}, new double[] {0.0d, 1.0d});
        assertThat(Double.isFinite(score)).isTrue();
        assertThat(score).isBetween(0.0d, 1.0d);
    }

    @Test
    void normalizedFactDensityAtZeroIsZero() {
        assertThat(InformationGainCalculator.normalizedFactDensity(0.0d)).isZero();
    }

    @Test
    void geoInformationGainMultipliesComponents() {
        double[] p = {0.5d, 0.5d};
        double jsd = InformationGainCalculator.jensenShannonDivergenceBits(p, p.clone());
        assertThat(jsd).isZero();
        double g = InformationGainCalculator.geoInformationGain(0.8d, 10.0d, p, p.clone());
        assertThat(g).isZero();
    }

    @Test
    void normalizedFactDensityMatchesDirectFormula() {
        double sd = 5.0d;
        double expected = 1.0d - Math.exp(-0.1d * sd);
        assertThat(InformationGainCalculator.normalizedFactDensity(sd)).isCloseTo(expected, within(TIGHT));
    }
}
