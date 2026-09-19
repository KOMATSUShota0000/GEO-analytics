package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** #61: 自社サイト本文の詰め込みを減点する。閾値未満は減点0、超過で単調減少。 */
class StuffingPenaltyCalculatorTest {

    @Test
    void 閾値以下では減点しない() {
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.0)).isEqualTo(1.0);
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.01)).isEqualTo(1.0);
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.03)).isEqualTo(1.0);
    }

    @Test
    void 閾値を超えると単調に減る() {
        double a = StuffingPenaltyCalculator.retentionFactor(0.05);
        double b = StuffingPenaltyCalculator.retentionFactor(0.08);
        double c = StuffingPenaltyCalculator.retentionFactor(0.12);

        assertThat(a).isLessThan(1.0).isGreaterThan(b);
        assertThat(b).isGreaterThan(c);
        assertThat(c).isGreaterThan(0.0);
    }

    @Test
    void 閾値直後の減り方はゆるやか() {
        // Why: 詰め込みは程度問題。閾値を少し超えただけで大きく減点するのは実態に合わない。
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.031)).isCloseTo(1.0, within(0.01));
    }

    @Test
    void 極端な密度では全減点になる() {
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.15)).isEqualTo(0.0);
        assertThat(StuffingPenaltyCalculator.retentionFactor(0.9)).isEqualTo(0.0);
    }

    @Test
    void 異常値は減点なしとして扱う() {
        assertThat(StuffingPenaltyCalculator.retentionFactor(Double.NaN)).isEqualTo(1.0);
        assertThat(StuffingPenaltyCalculator.retentionFactor(-0.5)).isEqualTo(1.0);
    }
}
