package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class PSquarePredictorTest {

    private static final double EPS = 1e-14d;

    private static double bdToDouble(BigDecimal value) {
        return value.doubleValue();
    }

    @Test
    void parabolicRejectsInvalidD() {
        assertThatThrownBy(() -> PSquarePredictor.parabolic(0.0d, 0.0d, 0.0d, 1.0d, 0.0d, 2.0d, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PSquarePredictor.parabolic(0.0d, 0.0d, 0.0d, 1.0d, 0.0d, 2.0d, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linearRejectsInvalidD() {
        assertThatThrownBy(() -> PSquarePredictor.linear(0.0d, 0.0d, 1.0d, 2.0d, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PSquarePredictor.linear(0.0d, 0.0d, 1.0d, 2.0d, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parabolicRejectsNonIncreasingMarkers() {
        assertThatThrownBy(() -> PSquarePredictor.parabolic(10.0d, 3.0d, 20.0d, 3.0d, 40.0d, 6.0d, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PSquarePredictor.parabolic(10.0d, 1.0d, 20.0d, 6.0d, 40.0d, 5.0d, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linearRejectsCoincidentMarkers() {
        assertThatThrownBy(() -> PSquarePredictor.linear(20.0d, 5.0d, 40.0d, 5.0d, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parabolicMatchesReferenceForPositiveShift() {
        double qIm1 = 10.0d;
        double nIm1 = 1.0d;
        double qI = 20.0d;
        double nI = 3.0d;
        double qIp1 = 40.0d;
        double nIp1 = 6.0d;
        int d = 1;
        BigDecimal bdQIm1 = BigDecimal.valueOf(qIm1);
        BigDecimal bdNIm1 = BigDecimal.valueOf(nIm1);
        BigDecimal bdQI = BigDecimal.valueOf(qI);
        BigDecimal bdNI = BigDecimal.valueOf(nI);
        BigDecimal bdQIp1 = BigDecimal.valueOf(qIp1);
        BigDecimal bdNIp1 = BigDecimal.valueOf(nIp1);
        BigDecimal bdD = BigDecimal.valueOf(d);
        MathContext mc = new MathContext(80, RoundingMode.HALF_EVEN);
        BigDecimal dnLeft = bdNI.subtract(bdNIm1);
        BigDecimal dnRight = bdNIp1.subtract(bdNI);
        BigDecimal dnSpan = bdNIp1.subtract(bdNIm1);
        BigDecimal slopeLeft = bdQI.subtract(bdQIm1).divide(dnLeft, mc);
        BigDecimal slopeRight = bdQIp1.subtract(bdQI).divide(dnRight, mc);
        BigDecimal c1 = dnLeft.add(bdD);
        BigDecimal c2 = dnRight.subtract(bdD);
        BigDecimal inner = c1.multiply(slopeRight).add(c2.multiply(slopeLeft));
        BigDecimal expectedBd = bdQI.add(bdD.divide(dnSpan, mc).multiply(inner));
        double expected = bdToDouble(expectedBd);
        assertThat(PSquarePredictor.parabolic(qIm1, nIm1, qI, nI, qIp1, nIp1, d)).isCloseTo(expected, within(EPS));
    }

    @Test
    void parabolicMatchesReferenceForNegativeShift() {
        double qIm1 = 10.0d;
        double nIm1 = 1.0d;
        double qI = 20.0d;
        double nI = 3.0d;
        double qIp1 = 40.0d;
        double nIp1 = 6.0d;
        int d = -1;
        BigDecimal bdQIm1 = BigDecimal.valueOf(qIm1);
        BigDecimal bdNIm1 = BigDecimal.valueOf(nIm1);
        BigDecimal bdQI = BigDecimal.valueOf(qI);
        BigDecimal bdNI = BigDecimal.valueOf(nI);
        BigDecimal bdQIp1 = BigDecimal.valueOf(qIp1);
        BigDecimal bdNIp1 = BigDecimal.valueOf(nIp1);
        BigDecimal bdD = BigDecimal.valueOf(d);
        MathContext mc = new MathContext(80, RoundingMode.HALF_EVEN);
        BigDecimal dnLeft = bdNI.subtract(bdNIm1);
        BigDecimal dnRight = bdNIp1.subtract(bdNI);
        BigDecimal dnSpan = bdNIp1.subtract(bdNIm1);
        BigDecimal slopeLeft = bdQI.subtract(bdQIm1).divide(dnLeft, mc);
        BigDecimal slopeRight = bdQIp1.subtract(bdQI).divide(dnRight, mc);
        BigDecimal c1 = dnLeft.add(bdD);
        BigDecimal c2 = dnRight.subtract(bdD);
        BigDecimal inner = c1.multiply(slopeRight).add(c2.multiply(slopeLeft));
        BigDecimal expectedBd = bdQI.add(bdD.divide(dnSpan, mc).multiply(inner));
        double expected = bdToDouble(expectedBd);
        assertThat(PSquarePredictor.parabolic(qIm1, nIm1, qI, nI, qIp1, nIp1, d)).isCloseTo(expected, within(EPS));
    }

    @Test
    void linearMatchesReferenceForPositiveShift() {
        double qI = 20.0d;
        double nI = 3.0d;
        double qAtD = 40.0d;
        double nAtD = 6.0d;
        int d = 1;
        BigDecimal bdQI = BigDecimal.valueOf(qI);
        BigDecimal bdNI = BigDecimal.valueOf(nI);
        BigDecimal bdQAtD = BigDecimal.valueOf(qAtD);
        BigDecimal bdNAtD = BigDecimal.valueOf(nAtD);
        BigDecimal bdD = BigDecimal.valueOf(d);
        MathContext mc = new MathContext(80, RoundingMode.HALF_EVEN);
        BigDecimal dn = bdNAtD.subtract(bdNI);
        BigDecimal expectedBd = bdQI.add(bdD.multiply(bdQAtD.subtract(bdQI)).divide(dn, mc));
        double expected = bdToDouble(expectedBd);
        assertThat(PSquarePredictor.linear(qI, nI, qAtD, nAtD, d)).isCloseTo(expected, within(EPS));
    }

    @Test
    void linearMatchesReferenceForNegativeShift() {
        double qI = 20.0d;
        double nI = 3.0d;
        double qAtD = 40.0d;
        double nAtD = 6.0d;
        int d = -1;
        BigDecimal bdQI = BigDecimal.valueOf(qI);
        BigDecimal bdNI = BigDecimal.valueOf(nI);
        BigDecimal bdQAtD = BigDecimal.valueOf(qAtD);
        BigDecimal bdNAtD = BigDecimal.valueOf(nAtD);
        BigDecimal bdD = BigDecimal.valueOf(d);
        MathContext mc = new MathContext(80, RoundingMode.HALF_EVEN);
        BigDecimal dn = bdNAtD.subtract(bdNI);
        BigDecimal expectedBd = bdQI.add(bdD.multiply(bdQAtD.subtract(bdQI)).divide(dn, mc));
        double expected = bdToDouble(expectedBd);
        assertThat(PSquarePredictor.linear(qI, nI, qAtD, nAtD, d)).isCloseTo(expected, within(EPS));
    }
}
