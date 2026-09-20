package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.BusinessModelType;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** #84: Google マップの実体が無い業種で基礎スコアが75点で頭打ちになる問題の是正。 */
class FactBasedScoreWeightsTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(0.05);

    @Test
    void 地域業種は配分を変えない() {
        assertThat(FactBasedScoreWeights.nonLocalRedistributionFactor(BusinessModelType.LOCAL_STORE))
                .isEqualTo(1.0);
        assertThat(FactBasedScoreWeights.meoScoreFor(BusinessModelType.LOCAL_STORE, 18.0)).isEqualTo(18.0);
    }

    @Test
    void 非地域業種はMEO枠を落として残り2軸へ比例配分する() {
        for (BusinessModelType mode :
                new BusinessModelType[] {BusinessModelType.CORPORATE_SERVICE, BusinessModelType.ONLINE_SERVICE}) {
            double factor = FactBasedScoreWeights.nonLocalRedistributionFactor(mode);
            // AI 50 + 機械可読性 25 = 75 を 100 へ引き上げる
            assertThat(50.0 * factor + 25.0 * factor).isCloseTo(100.0, TOLERANCE);
            // 実測の MEO 点があっても採らない（枠ごと落とす）
            assertThat(FactBasedScoreWeights.meoScoreFor(mode, 18.0)).isEqualTo(0.0);
        }
    }

    @Test
    void 非地域業種でも満点に到達できる() {
        BusinessModelType mode = BusinessModelType.CORPORATE_SERVICE;
        double factor = FactBasedScoreWeights.nonLocalRedistributionFactor(mode);
        double total = FactBasedScoreAggregator.aggregate(
                50.0 * factor, FactBasedScoreWeights.meoScoreFor(mode, 0.0), 25.0 * factor);

        assertThat(total).isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void 地域業種の満点構成は従来どおり() {
        double total = FactBasedScoreAggregator.aggregate(50.0, FactBasedScoreWeights.maxMeoScore(), 25.0);

        assertThat(total).isEqualTo(100.0);
    }
}
