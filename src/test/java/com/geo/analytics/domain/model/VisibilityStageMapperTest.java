package com.geo.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class VisibilityStageMapperTest {

    @Test
    void progressRate_atFibonacciBandEdges_isOneOrZero() {
        assertThat(VisibilityStageMapper.define(1, 1).progressRate()).isEqualTo(1.0);
        assertThat(VisibilityStageMapper.define(1, 2).progressRate()).isEqualTo(0.0);

        assertThat(VisibilityStageMapper.define(2, 2).progressRate()).isEqualTo(1.0);
        assertThat(VisibilityStageMapper.define(2, 3).progressRate()).isEqualTo(0.0);

        assertThat(VisibilityStageMapper.define(3, 3).progressRate()).isEqualTo(1.0);
        assertThat(VisibilityStageMapper.define(3, 5).progressRate()).isEqualTo(0.0);

        assertThat(VisibilityStageMapper.define(4, 5).progressRate()).isEqualTo(1.0);
        assertThat(VisibilityStageMapper.define(4, 8).progressRate()).isEqualTo(0.0);

        assertThat(VisibilityStageMapper.define(5, 8).progressRate()).isEqualTo(1.0);
        assertThat(VisibilityStageMapper.define(5, 13).progressRate()).isEqualTo(0.0);
    }

    @Test
    void progressRate_nullOrNonPositive_isZero() {
        assertThat(VisibilityStageMapper.define(5, null).progressRate()).isEqualTo(0.0);
        assertThat(VisibilityStageMapper.define(5, 0).progressRate()).isEqualTo(0.0);
        assertThat(VisibilityStageMapper.define(5, -3).progressRate()).isEqualTo(0.0);
    }

    @Test
    void progressRate_logModel_increasesFasterNearLowerBound() {
        double pNearLo = VisibilityStageMapper.define(10, 90).progressRate();
        double pMid = VisibilityStageMapper.define(10, 113).progressRate();
        double pNearHi = VisibilityStageMapper.define(10, 140).progressRate();
        assertThat(pNearLo).isGreaterThan(pMid);
        assertThat(pMid).isGreaterThan(pNearHi);
        assertThat(pMid).isCloseTo(0.5, within(0.07));

        double pA = VisibilityStageMapper.define(3, 3).progressRate();
        double pB = VisibilityStageMapper.define(3, 4).progressRate();
        double pC = VisibilityStageMapper.define(3, 5).progressRate();
        assertThat(pA).isEqualTo(1.0);
        assertThat(pB).isGreaterThan(pC);
        assertThat(pC).isEqualTo(0.0);
    }
}
