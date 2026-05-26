package com.geo.analytics.domain.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class JapaneseTextNormalizerTest {

    @Test
    void shouldReturnNullWhenInputIsNull() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText(null)).isNull();
    }

    @Test
    void shouldNfkcNormalizeHalfWidthKatakanaToFullWidthWithoutBrandAliasChange() {
        String halfWidthNikon = "\uff86\uff7a\uff9d";
        assertThat(JapaneseTextNormalizer.normalizeBrandText(halfWidthNikon)).isEqualTo("\u30cb\u30b3\u30f3");
    }

    @Test
    void shouldNfkcNormalizeHalfWidthBicCameraExampleFromUserSpec() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("\uff8b\uff9e\uff6f\uff78\uff76\uff92\uff97"))
                .isEqualTo("\u30d3\u30c4\u30af\u30ab\u30e1\u30e9");
    }

    @Test
    void shouldNfkcNormalizeFullWidthAsciiToHalfWidth() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("\uff33\uff2f\uff2e\uff39")).isEqualTo("SONY");
    }

    @Test
    void shouldStripLeadingKabushikiGaisha() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("\u682a\u5f0f\u4f1a\u793e\u30ad\u30e4\u30ce\u30f3"))
                .isEqualTo("\u30ad\u30e4\u30ce\u30f3");
    }

    @Test
    void shouldStripTrailingKabushikiParenthesis() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("\u30ad\u30e4\u30ce\u30f3(\u682a)"))
                .isEqualTo("\u30ad\u30e4\u30ce\u30f3");
    }

    @Test
    void shouldStripEnglishCorpSuffix() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("Sony Corp.")).isEqualTo("Sony ");
    }

    @Test
    void shouldStripChainedLegalPrefixesWithoutInfiniteLoop() {
        assertThat(
                        JapaneseTextNormalizer.normalizeBrandText(
                                "\u682a\u5f0f\u4f1a\u793e(\u682a)\u30ad\u30e4\u30ce\u30f3"))
                .isEqualTo("\u30ad\u30e4\u30ce\u30f3");
    }

    @Test
    void shouldStripMultipleDistinctLegalEntitiesSequentially() {
        assertThat(
                        JapaneseTextNormalizer.normalizeBrandText(
                                "\u6709\u9650\u4f1a\u793e\u5408\u540c\u4f1a\u793e\u30d3\u30c3\u30af\u30ab\u30e1\u30e9"))
                .isEqualTo("\u30d3\u30c4\u30af\u30ab\u30e1\u30e9");
    }

    @Test
    void shouldMapCanonAliasToKiyanonKatakana() {
        assertThat(JapaneseTextNormalizer.normalizeBrandText("\u30ad\u30e3\u30ce\u30f3"))
                .isEqualTo("\u30ad\u30e4\u30ce\u30f3");
    }

    @Test
    void shouldCompleteWithoutExceptionForSupplementaryIdeograph() {
        String input = "\uD842\uDFB7\u91CE\u5BB6";
        assertThatCode(() -> JapaneseTextNormalizer.normalizeBrandText(input)).doesNotThrowAnyException();
        assertThat(JapaneseTextNormalizer.normalizeBrandText(input)).isEqualTo(input);
    }
}
