package com.geo.analytics.infrastructure.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SerpSearchQueryBuilderTest {

    @Test
    void combinesBrandAndKeywordWithSpace() {
        assertThat(SerpSearchQueryBuilder.build("ハーゲンダッツ", "アイス")).isEqualTo("ハーゲンダッツ アイス");
    }

    @Test
    void doesNotDuplicateWhenKeywordAlreadyContainsBrand() {
        assertThat(SerpSearchQueryBuilder.build("ハーゲンダッツ", "ハーゲンダッツ 価格")).isEqualTo("ハーゲンダッツ 価格");
    }

    @Test
    void keywordOnlyWhenBrandBlank() {
        assertThat(SerpSearchQueryBuilder.build("  ", "アイス")).isEqualTo("アイス");
    }
}
