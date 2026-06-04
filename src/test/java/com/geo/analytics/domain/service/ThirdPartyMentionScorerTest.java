package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThirdPartyMentionScorerTest {

    private static final String SELF = "https://myco.jp";

    @Test
    void emptyOrNull_yieldsZero() {
        assertThat(ThirdPartyMentionScorer.scoreFromMentionUrls(null, SELF)).isEqualTo(0.0);
        assertThat(ThirdPartyMentionScorer.scoreFromMentionUrls(List.of(), SELF)).isEqualTo(0.0);
    }

    @Test
    void excludesSelfDomainAndSubdomains() {
        // 自社ページ・自社サブドメインは第三者から除外。残る独立ドメインは prtimes.jp と note.com の2件。
        var urls = List.of(
                "https://myco.jp/about",
                "https://blog.myco.jp/post",
                "https://prtimes.jp/main/x",
                "https://note.com/someone/y");
        assertThat(ThirdPartyMentionScorer.distinctThirdPartyDomainCount(urls, SELF)).isEqualTo(2);
        // 20 * (2/8) = 5.0
        assertThat(ThirdPartyMentionScorer.scoreFromMentionUrls(urls, SELF)).isEqualTo(5.0);
    }

    @Test
    void normalizesWwwAndDedupesSameDomain() {
        // www有無は同一ドメイン。prtimes.jp は1ドメインとして数える。
        var urls = List.of(
                "https://www.prtimes.jp/a",
                "https://prtimes.jp/b",
                "http://www.prtimes.jp/c");
        assertThat(ThirdPartyMentionScorer.distinctThirdPartyDomainCount(urls, SELF)).isEqualTo(1);
    }

    @Test
    void saturatesAtMax() {
        // 8件以上の独立ドメインで満点20に飽和。
        var urls = List.of(
                "https://a.com", "https://b.com", "https://c.com", "https://d.com",
                "https://e.com", "https://f.com", "https://g.com", "https://h.com",
                "https://i.com", "https://j.com");
        assertThat(ThirdPartyMentionScorer.distinctThirdPartyDomainCount(urls, SELF)).isEqualTo(10);
        assertThat(ThirdPartyMentionScorer.scoreFromMentionUrls(urls, SELF))
                .isEqualTo(ThirdPartyMentionScorer.MAX_AUTHORITY_CORE);
    }

    @Test
    void scalesLinearlyBelowSaturation() {
        var urls = List.of("https://a.com", "https://b.com", "https://c.com", "https://d.com");
        // 20 * (4/8) = 10.0
        assertThat(ThirdPartyMentionScorer.scoreFromMentionUrls(urls, SELF)).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void malformedUrls_areSkipped() {
        var urls = List.of("not a url", "", "https://valid.com/x");
        assertThat(ThirdPartyMentionScorer.distinctThirdPartyDomainCount(urls, SELF)).isEqualTo(1);
    }
}
