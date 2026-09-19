package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.ReputationBand;
import org.junit.jupiter.api.Test;

/** #62: 感情の生値を独立した評判スコアへ変換する。 */
class ReputationScoreCalculatorTest {

    @Test
    void 中立はちょうど中間になる() {
        assertThat(ReputationScoreCalculator.percent(0.0)).isEqualTo(50);
    }

    @Test
    void 誤差レベルの揺れではスコアがほとんど動かない() {
        // Why: LLM の生値は 0.85 と 0.90 のように揺れる。飽和させないとこの揺れが点差として出てしまう。
        int high = ReputationScoreCalculator.percent(0.90);
        int slightlyLower = ReputationScoreCalculator.percent(0.85);

        assertThat(StrictMath.abs(high - slightlyLower)).isLessThanOrEqualTo(2);
    }

    @Test
    void 意味のある差は残る() {
        assertThat(ReputationScoreCalculator.percent(0.70) - ReputationScoreCalculator.percent(0.40))
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    void 外れ値は飽和して範囲を超えない() {
        assertThat(ReputationScoreCalculator.percent(1.0)).isBetween(90, 100);
        assertThat(ReputationScoreCalculator.percent(-1.0)).isBetween(0, 10);
        assertThat(ReputationScoreCalculator.percent(5.0)).isEqualTo(ReputationScoreCalculator.percent(1.0));
        assertThat(ReputationScoreCalculator.percent(-5.0)).isEqualTo(ReputationScoreCalculator.percent(-1.0));
    }

    @Test
    void 帯は閾値で切り替わる() {
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(0.60)))
                .isEqualTo(ReputationBand.HIGH);
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(0.10)))
                .isEqualTo(ReputationBand.MEDIUM);
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(-0.50)))
                .isEqualTo(ReputationBand.LOW);
    }

    @Test
    void 実測の分布が帯に素直に対応する() {
        // 実測（AI Overview 20件）は生値 0.0〜0.9。0.4 以上は「高」、0.1 前後は「中」になる。
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(0.90)))
                .isEqualTo(ReputationBand.HIGH);
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(0.40)))
                .isEqualTo(ReputationBand.HIGH);
        assertThat(ReputationScoreCalculator.band(ReputationScoreCalculator.percent(0.0)))
                .isEqualTo(ReputationBand.MEDIUM);
    }
}
