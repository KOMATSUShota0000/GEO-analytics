package com.geo.analytics.infrastructure.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoCompetitorQueryBuilderTest {

    @Test
    void combinesBrandAndKeywordWithSpace() {
        assertThat(GeoCompetitorQueryBuilder.build("ハーゲンダッツ", "アイス")).isEqualTo("ハーゲンダッツ アイス");
    }

    @Test
    void doesNotDuplicateWhenKeywordAlreadyContainsBrand() {
        assertThat(GeoCompetitorQueryBuilder.build("ハーゲンダッツ", "ハーゲンダッツ 価格")).isEqualTo("ハーゲンダッツ 価格");
    }

    @Test
    void keywordOnlyWhenBrandBlank() {
        assertThat(GeoCompetitorQueryBuilder.build("  ", "アイス")).isEqualTo("アイス");
    }
}
